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
   [toaster.ring :as ring]
   [toaster.views :as views]
   [toaster.profiles :as profile])
  (:gen-class))

(defonce config (conf/load-config "toaster" conf/default-settings))

(defn auth-wrap
  "Comfortably wrap routes using a function pointer to be called if an
  account is correctly authenticated in the currently running session.
  (fun) is passed 3 hash-maps args: request, config and account."
  [request fun]
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
     (s/render [:body
                (s/notify (f/message e) "is-error")
                (s/resource s/login)]))))

(defroutes
  app-routes

  (GET "/" request
       (f/attempt-all
        [db (s/check-database)
         conf (s/check-config request)]
        (f/if-let-ok? [account (s/check-account request)]
          (s/render [:body (views/dashboard account)])
          ;; else
          (s/render [:body (s/resource s/login)]))
        (f/when-failed[e]
          (s/render [:body (s/error "backend missing" e)]))))

  ;; NEW ROUTES HERE
  (POST "/dockerfile" request
        (->> (fn [req conf acct]
               (s/render acct
                         [:body
                          (s/upload req conf acct views/add-job)
                          (views/dashboard acct)]))
             (auth-wrap request)))

  (POST "/remove" request
        (->> (fn [req conf acct]
               (s/render acct [:body
                               (views/remove-job req conf acct)
                               (views/dashboard acct)]))
             (auth-wrap request)))

  (POST "/start" request
        (->> (fn [req conf acct]
               (s/render acct [:body
                               (views/start-job req conf acct)
                               (views/dashboard acct)]))
             (auth-wrap request)))

  (POST "/view" request
        (->> (fn [req conf acct]
               (s/render acct [:body
                               (views/view-job req conf acct)
                               (views/dashboard acct)]))
             (auth-wrap request)))

  (GET "/error" request
       (->> (fn [req conf acct]
              (s/render acct [:body
                              (s/notify "Generic Error Page" "is-error")
                              (views/dashboard acct)]))
            (auth-wrap request)))

  ;; JUST-AUTH ROUTES
  (GET "/login" request (s/render-template s/login))

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
                 (s/render logged [:body (views/dashboard logged)])))
         (f/when-failed [e]
           (s/render
            [:body
             (s/error "Login failed" e)]))))

  (GET "/logout" request
       (conj {:session {:config config}}
             (s/render [:body
                        [:h1 {:class "title"} "Logged out."]])))

  (GET "/signup" request (s/render-template s/signup))
  (POST "/signup" request
        (s/adduser request
                   (fn [name email]
                     (s/render
                      [:body
                       (s/notify (str "Account created: "
                                      name " &lt;" email "&gt;") "is-success")
                       [:h1 {:class "title"} "Check email for activation."]]))))

  (GET "/activate/:email/:activation-id"
       [email activation-id :as request]
       (let [activation-uri
             (str "http://"
                  (get-in request [:headers "host"])
                  "/activate/" email "/" activation-id)]
         (f/attempt-all
          [act (auth/activate-account
                @ring/auth email
                {:activation-link activation-uri})]
          (s/render
           [:body
            (s/notify "Account succesfully activated" "is-success")
            (views/dashboard)])
          (f/when-failed [e]
            (s/render
             [:body
              (s/error "Failure activating account" e)])))))

  ;; -- end of JUST-AUTH

  (POST "/" request
        ;; generic endpoint for canceled operations
        (s/render (s/check-account request)
                  (s/notify
                   (s/param request :message) "is-error")))

  (route/resources "/")
  (route/not-found (s/render [:body (s/notify "Page Not Found" "is-error")]))

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
