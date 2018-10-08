(ns toaster.jobs
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
    [toaster.webpage :as web]
    [toaster.config :refer :all]
    [toaster.ring :refer [jobs]]
    [hiccup.form :as hf]))

(defn- ssh-host [config]
  (str (q config [:jenkins :user]) "@" (q config [:jenkins :host])))


(defn- sync_jobs [config arg1 arg2]
  (with-programs
    [ssh]
    (try
      (-> (ssh "-i" (q config [:jenkins :key])
               (ssh-host config) "sync_jobs.py"
               arg1 arg2)
          (str/split #"\n"))
      (catch Exception e (f/fail (str "ERROR in sync_jobs.py - " (.getMessage e)))))))

(defn- dockerlint [path]
  (with-programs [node]
                 (try
                   (node "node_modules/dockerlint/bin/dockerlint.js" path)
                   (catch Exception e
                     (f/fail (str "ERROR in dockerlint - " (.getMessage e)))))))


(defn add [path config account]
  (with-programs [ssh scp node]
                 (let [tstamp (tc/to-long (time/now))
                       jobname (str (:email account) "-vm_amd64-" tstamp)
                       jobdir (str "/srv/toaster/" jobname)]
                   (f/attempt-all
                     [r_lint (dockerlint path)
                      r_mkdir (ssh "-i" (q config [:jenkins :key])
                                   (ssh-host config) "mkdir" "-p" jobdir)
                      r_scp (scp "-i" (q config [:jenkins :key])
                                 path (str (ssh-host config) ":" jobdir))
                      r_job (log/spy (sync_jobs config "-a" jobname))
                      r_store (log/spy (db/store!
                                         @jobs :jobid
                                         (log/spy {:jobid     jobname
                                                   :email     (:email account)
                                                   :account   (dissoc account :password :activation-link)
                                                   :lint      (if (.contains r_lint "is OK") true false)
                                                   :timestamp tstamp
                                                   :type      "vm_amd64"})))]
                     {:lint r_lint
                      :job  r_job}
                     (f/when-failed [e]
                                    (web/render-error-page
                                      (str "Job add failure: " (f/message e))))))))


(defn listall [config account]
  (sync_jobs config "-l" (:email account))
  ;;(db/query @jobs {:email (:email account)})
  )


