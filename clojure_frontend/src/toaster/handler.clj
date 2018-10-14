;; Copyright (C) 2015-2018 Dyne.org foundation

;; Sourcecode designed, written and maintained by
;; Denis Roio <jaromil@dyne.org>

;; This program is free software: you can redistribute it and/or modify
;; it under the terms of the GNU Affero General Public License as published by
;; the Free Software Foundation, either version 3 of the License, or
;; (at your option) any later version.

;; This program is distributed in the hope that it will be useful,
;; but WITHOUT ANY WARRANTY; without even the implied warranty of
;; MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
;; GNU Affero General Public License for more details.

;; You should have received a copy of the GNU Affero General Public License
;; along with this program.  If not, see <http://www.gnu.org/licenses/>.

(ns toaster.handler
  (:require
   [clojure.string :as str]
   [clojure.java.io :as io]
   [clojure.data.json :as json]
   [compojure.core :refer :all]
   [compojure.handler :refer :all]
   [compojure.route :as route]
   [compojure.response :as response]

   [ring.adapter.jetty :refer :all]
   [ring.middleware.session :refer :all]
   [ring.middleware.accept :refer [wrap-accept]]
   [ring.middleware.defaults :refer [wrap-defaults site-defaults]]

   [failjure.core :as f]
   [taoensso.timbre :as log]
   [just-auth.core :as auth]

   [toaster.session :as s]
   [toaster.config :as conf]
   [toaster.bulma :as web :refer [render notify login-form]]
   [toaster.ring :as ring]
   [toaster.views :as views]
   [toaster.jobs :as job])
  (:gen-class))

(defonce config (conf/load-config "toaster" conf/default-settings))


(defn auth-wrap [request fun]
  (f/attempt-all
   [db      (s/check-database)
    config  (s/check-config request)
    account (if (conf/q config [:webserver :mock-auth])
              {:email "mock@dyne.org"
               :name "MockUser"
               :activated true}
              ;; else
              (s/check-account request))]
   (fun request config account)
   (f/when-failed [e]
     (web/render [:body
                  (web/notify (f/message e) "is-error")
                  web/login-form]))))

(defn- login-page [request form]
  (f/attempt-all
   [acct (s/check-account request)]
   (web/render acct
               [:body
                [:h1 {:class "title"}
                 (str "Already logged in with account: "
                      (:email acct))]
                [:h2 {:class "subtitle"}
                 [:a {:href "/logout"} "Logout"]]])
   (f/when-failed [e]
     (web/render [:body form]))))

(defroutes
  app-routes

  (GET "/" request
       (f/attempt-all
        [db (s/check-database)
         conf (s/check-config request)]
        (f/if-let-ok? [account (s/check-account request)]
          (web/render [:body (views/dashboard account)])
          ;; else
          (web/render [:body web/login-form]))
        (f/when-failed[e]
          (web/render [:body (web/notify (f/message e) "is-error")]))))

  ;; NEW ROUTES HERE
  (POST "/dockerfile" request
        (->> (fn [req conf acct]
               (web/render acct
                           [:body
                            (views/dockerfile-upload-post req conf acct)
                            (views/dashboard acct)]))
             (auth-wrap request)))

  (POST "/remove" request
        (->> (fn [req conf acct]
               (web/render acct [:body
                                 (views/remove-job req conf acct)
                                 (views/dashboard acct)]))
             (auth-wrap request)))

  (POST "/start" request
        (->> (fn [req conf acct]
               (web/render acct [:body
                                 (views/start-job req conf acct)
                                 (views/dashboard acct)]))
             (auth-wrap request)))

  (POST "/view" request
        (->> (fn [req conf acct]
               (web/render acct [:body
                                 (views/view-job req conf acct)
                                 (views/dashboard acct)]))
             (auth-wrap request)))

  (GET "/error" request
       (->> (fn [req conf acct]
              (web/render acct [:body
                                (web/notify "Generic Error Page" "is-error")
                                (views/dashboard acct)]))
            (auth-wrap request)))

  ;; JUST-AUTH ROUTES
  (GET "/login" request (login-page request web/login-form))

  (POST "/login" request
        (f/attempt-all
         [username (s/param request :username)
          password (s/param request :password)
          logged (auth/sign-in
                  @ring/auth username password {})]
         ;; TODO: pass :ip-address in last argument map
         (let [session {:session {:config config
                                  :auth   logged}}]
           (conj session
                 (web/render logged [:body (views/dashboard logged)])))
         ;; (web/render
         ;;  logged
         ;;  [:div
         ;;   [:h1 "Logged in: " username]
         ;;   views/welcome-menu])))
         (f/when-failed [e]
           (web/render [:body (web/notify
                               (str "Login failed: " (f/message e)) "is-error")]))))

  (GET "/logout" request
       (conj {:session {:config config}}
             (web/render [:body
                          [:h1 {:class "title"} "Logged out."]])))

  (GET "/signup" request (login-page request web/signup-form))
  (POST "/signup" request
        (f/attempt-all
         [name (s/param request :name)
          email (s/param request :email)
          password (s/param request :password)
          repeat-password (s/param request :repeat-password)
          activation {:activation-uri
                      (get-in request [:headers "host"])}]
         (web/render
          (if (= password repeat-password)
            (f/try*
             (f/if-let-ok?
                 [signup (auth/sign-up @ring/auth
                                       name
                                       email
                                       password
                                       activation
                                       [])]
               [:body
                [:h2 (str "Account created: "
                          name " &lt;" email "&gt;")]
                [:h3 "Account pending activation."]]
               [:body
                (web/notify
                 (str "Failure creating account: "
                      (f/message signup)) "is-error")
                (login-page request web/signup-form)]))
            [:body (web/notify
                    "Repeat password didnt match" "is-error")]))
         (f/when-failed [e]
           (web/render
            [:body (web/notify
                    (str "Sign-up failure: " (f/message e)) "is-error")]))))

  (GET "/activate/:email/:activation-id"
       [email activation-id :as request]
       (let [activation-uri
             (str "http://"
                  (get-in request [:headers "host"])
                  "/activate/" email "/" activation-id)]
         (web/render
          [:body
           (f/if-let-failed?
               [act (auth/activate-account
                     @ring/auth email
                     {:activation-link activation-uri})]
             (web/notify
              [:span
               [:h1 {:class "title"}    "Failure activating account"]
               [:h2 {:class "subtitle"} (f/message act)]
               [:p (str "Email: " email " activation-id: " activation-id)]] "is-error")
             (web/notify [:h1 {:class "title"} (str "Account activated - " email)] "is-success"))])))
  ;; -- end of JUST-AUTH

  (POST "/" request
        ;; generic endpoint for canceled operations
        (web/render (s/check-account request)
                    (web/notify
                     (s/param request :message) "is-error")))

  (route/resources "/")
  (route/not-found (web/render [:body (web/notify "Page Not Found" "is-error")]))

  )                                                         ;; end of routes

(def app
  (-> (wrap-defaults app-routes ring/app-defaults)
      (wrap-accept {:mime     ["text/html"]
                    ;; preference in language, fallback to english
                    :language ["en" :qs 0.5
                               "it" :qs 1
                               "nl" :qs 1
                               "hr" :qs 1]})
      (wrap-session)))

;; for uberjar
(defn -main []
  (println "Starting ring server")
  (ring/init ring/app-defaults)
                                        ;(run-jetty app {:port 6060
                                        ;                :host "localhost"
                                        ;                :join? true})
  )
