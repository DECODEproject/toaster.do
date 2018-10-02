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
     [toaster.webpage :as web]
     [toaster.ring :as ring])
    (:import java.io.File)
    (:gen-class))

(defonce config (conf/load-config "toaster" conf/default-settings))

(defroutes app-routes

  (GET "/" request (web/render "Hello World!"));; web/readme))

  ;; NEW ROUTES HERE




  ;; JUST-AUTH ROUTES
  (GET "/login" request
       (f/attempt-all
        [acct (s/check-account request)]
        (web/render acct
                    [:div
                     [:h1 (str "Already logged in with account: "
                               (:email acct))]
                     [:h2 [:a {:href "/logout"} "Logout"]]])
        (f/when-failed [e]
          (web/render web/login-form))))
  (POST "/login" request
        (f/attempt-all
         [username (s/param request :username)
          password (s/param request :password)
          logged (auth/sign-in
                  @ring/auth username password {})]
         ;; TODO: pass :ip-address in last argument map
         (let [session {:session {:config config
                                  :auth logged}}]
           (conj session
                 (web/render
                  logged
                  [:div
                   [:h1 "Logged in: " username]
                   (web/render-yaml session)])))
         (f/when-failed [e]
           (web/render-error-page
            (str "Login failed: " (f/message e))))))
  (GET "/session" request
       (-> (:session request) web/render-yaml web/render))
  (GET "/logout" request
       (conj {:session {:config config}}
             (web/render [:h1 "Logged out."])))
  (GET "/signup" request
       (web/render web/signup-form))
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
               [:div
                [:h2 (str "Account created: "
                          name " &lt;" email "&gt;")]
                [:h3 "Account pending activation."]]
               (web/render-error
                (str "Failure creating account: "
                     (f/message signup)))))
            (web/render-error
               "Repeat password didnt match")))
         (f/when-failed [e]
           (web/render-error-page
            (str "Sign-up failure: " (f/message e))))))
  (GET "/activate/:email/:activation-id"
       [email activation-id :as request]
       (let [activation-uri
             (str "http://"
                  (get-in request [:headers "host"])
                  "/activate/" email "/" activation-id)]
         (web/render
          [:div
           (f/if-let-failed?
               [act (auth/activate-account
                     @ring/auth email
                     {:activation-link activation-uri})]
             (web/render-error
              [:div
               [:h1 "Failure activating account"]
               [:h2 (f/message act)]
               [:p (str "Email: " email " activation-id: " activation-id)]])
             [:h1 (str "Account activated - " email)])])))
  ;; -- end of JUST-AUTH

  (POST "/" request
        ;; generic endpoint for canceled operations
        (web/render (s/check-account request)
                    [:div {:class (str "alert alert-danger") :role "alert"}
                     (s/param request :message)]))

  (route/resources "/")
  (route/not-found (web/render-error-page "Page Not Found"))
  ) ;; end of routes

(def app
  (-> (wrap-defaults app-routes ring/app-defaults)
      (wrap-accept {:mime ["text/html"]
                    ;; preference in language, fallback to english
                    :language ["en" :qs 0.5
                               "it" :qs 1
                               "nl" :qs 1
                               "hr" :qs 1]})
      (wrap-session)))

;; for uberjar
(defn -main []
  (println "Starting standalone jetty server on http://localhost:6060")
  (run-jetty app {:port 6060
                  :host "localhost"
                  :join? true}))
