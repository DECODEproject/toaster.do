(ns toaster.profiles
  (:require
   [clojure.string :as str]
   [clojure.java.io :as io]
   [clj-time.core :as time]
   [clj-time.coerce :as tc]
   [clj-storage.db.mongo :refer [create-mongo-store]]
   [clj-storage.core :as db]
   [failjure.core :as f]
   [taoensso.timbre :as log :refer [debug]]
   [me.raynes.conch :as sh :refer [with-programs]]
   [toaster.config :refer :all]
   [toaster.ring :refer [jobs profiles]]
   [toaster.bulma :refer [render-yaml]]
   [hiccup.form :as hf]))

(defn create [id]
  (let [profile {:email id
                 :joblimit 3 ; default
                 :roles ["noob"]
                 :lastlogin (tc/to-long (time/now))}]
    (db/store! @profiles :email profile)
    (render-yaml profile)))

(defn- fetch-profile [id]
  (f/if-let-ok? [profile (db/fetch @profiles id)]
    profile
    (f/fail (str "Profile not found " id " :: " (f/message profile)))))

(defn get-joblimit [id]
  (f/ok-> id fetch-profile (get :joblimit)))

(defn has-role [id role]
  (f/ok-> id fetch-profile (get :roles)
          clojure.core/set (contains? role)))
  
