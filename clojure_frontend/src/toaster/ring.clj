;; Copyright (C) 2015-2017 Dyne.org foundation

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

(ns toaster.ring
  (:require
   [clojure.java.io :as io]
   [toaster.config :as conf]
   [taoensso.timbre :as log]
   [failjure.core :as f]
   [clj-storage.db.mongo :refer [get-mongo-db create-mongo-store]]
   [just-auth.core :as auth]
   [just-auth.db.just-auth :as auth-db]

   [auxiliary.translation :as trans]
   [compojure.core :refer :all]
   [compojure.handler :refer :all]
   [ring.middleware.session :refer :all]
   [ring.middleware.accept :refer [wrap-accept]]
   [ring.middleware.defaults :refer [wrap-defaults site-defaults]]))

(def config (atom {}))
(def db     (atom {}))
(def accts  (atom {}))
(def auth   (atom {}))

(defn init []
  (log/merge-config! {:level :debug
                      ;; #{:trace :debug :info :warn :error :fatal :report}

                      ;; Control log filtering by
                      ;; namespaces/patterns. Useful for turning off
                      ;; logging in noisy libraries, etc.:
;;                      :ns-whitelist  ["agiladmin.*" "just-auth.*"]
                      :ns-blacklist  ["org.eclipse.jetty.*"
                                      "org.mongodb.driver.cluster"]})

  ;; load configuration
  (reset! config (conf/load-config
                  (or (System/getenv "toaster_conf") "toaster")
                  conf/default-settings))

  (let [justauth-conf (get-in @config [:toaster :just-auth])]
    ;; connect database (TODO: take parameters from configuration)
    (reset! db (get-mongo-db (:mongo-url justauth-conf)))

    ;; create authentication stores in db
    (f/attempt-all
     [auth-conf   (get-in @config [:toaster :just-auth])
      auth-stores (auth-db/create-auth-stores @db)]

     [(trans/init "lang/auth-en.yml" "lang/english.yaml")
      (reset! accts auth-stores)
      (reset! auth (auth/email-based-authentication  
                    auth-stores
                    ;; TODO: replace with email taken from config
                    (dissoc (:just-auth (:toaster (conf/load-config
                                                    "toaster" conf/default-settings)))
                            :mongo-url :mongo-user :mongo-pass)
                    {:criteria #{:email :ip-address} 
                     :type :block
                     :time-window-secs 10
                     :threshold 5}))]
                    ;; (select-keys auth-stores [:account-store
                    ;;                           :password-recovery-store])
     (f/when-failed [e]
       (log/error (str (trans/locale [:init :failure])
                       " - " (f/message e))))))
  (log/info (str (trans/locale [:init :success])))
  (log/debug @auth))

(def app-defaults
    (-> site-defaults
        (assoc-in [:cookies] true)
        (assoc-in [:security :anti-forgery]
                  (get-in @config [:webserver :anti-forgery]))
        (assoc-in [:security :ssl-redirect]
                  (get-in @config [:webserver :ssl-redirect]))
        (assoc-in [:security :hsts] true)))
