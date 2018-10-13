(ns toaster.session
  (:refer-clojure :exclude [get])
  (:require
   [toaster.config :as conf]
   [taoensso.timbre :as log]
   [failjure.core :as f]
   [just-auth.core :as auth]
   [toaster.ring :as ring]
   [toaster.bulma :as web]))

(defn param [request param]
  (let [value
        (get-in request
                (conj [:params] param))]
    (if (nil? value)
      (f/fail (str "Parameter not found: " param))
      value)))

;; TODO: not working?
(defn get [req arrk]
  {:pre (coll? arrk)}
  (if-let [value (get-in req (conj [:session] arrk))]
    value
    (f/fail (str "Value not found in session: " (str arrk)))))

(defn check-config [request]
  ;; reload configuration from file all the time if in debug mode
  (if-let [session (:session request)]
    (if (contains? session :config)
      (:config session)
      (conf/load-config "toaster" conf/default-settings))
    (f/fail "Session not found.")))

(defn check-account [request]
  ;; check if login is present in session
  (f/attempt-all
   [login (get-in request [:session :auth :email])
    user (auth/get-account @ring/auth login)]
   user
   (f/when-failed [e]
     (->> e f/message
          (str "Unauthorized access: ")
          f/fail))))

(defn check-database []
  (if-let [db @ring/db]
    db
    (f/fail "No connection to database.")))
