(ns toaster.jobs
  (:require
   [clojure.string :as str]
   [clojure.java.io :as io]
   [clj-time.core :as time]
   [clj-time.coerce :as tc]
   [clj-storage.core :as db]
   [failjure.core :as f]
   [taoensso.timbre :as log :refer [debug]]
   [me.raynes.conch :as sh :refer [with-programs]]
   [toaster.config :refer :all]
   [toaster.ring :refer [jobs]]
   [toaster.profiles :as profile]
   [hiccup.form :as hf]))

;; list of arm targets to chose from
                                        ;"beagleboneblack"
                                        ;"chromeacer"
                                        ;"chromeveyron"
                                        ;"droid4"
                                        ;"n900"
                                        ;"odroidxu4"
                                        ;"odroidxu"
                                        ;"ouya"
                                        ;"raspi1"
                                        ;"raspi2"
                                        ;"raspi3"
                                        ;"rock64"
                                        ;"sunxi"
                                        ;"turbox-twister"

(defn- ssh-host [config]
  (str (q config [:jenkins :user]) "@" (q config [:jenkins :host])))


(defn sync_jobs [config arg1 arg2]
  (with-programs
    [ssh]
    (try
      (-> (ssh "-i" (q config [:jenkins :key])
               (ssh-host config) "sync_jobs.py"
               arg1 arg2)
          (str/split #"\n"))
      (catch Exception e (f/fail (str "ERROR in sync_jobs.py "
                                      arg1 " " arg2 " - " (.getMessage e)))))))

(defn- dockerlint [path]
  (with-programs [node]
    (try
      (node "node_modules/dockerlint/bin/dockerlint.js" path)
      (catch Exception e
        (f/fail (str "ERROR in dockerlint - " (.getMessage e)))))))

(defn count [id]
  (f/attempt-all
   [joblist (db/query @jobs {:email id})]
   (clojure.core/count joblist)
   (f/when-failed [e]
     (f/fail "jobs/count :: " (f/message e)))))

(defn add [path config account]
  (with-programs
    [ssh scp node]
    (let [tstamp (tc/to-long (time/now))
          jobname (str (:email account) "-vm_amd64_ascii-" tstamp)
          jobdir (str "/srv/toaster/" jobname)]
      (f/if-let-ok? [joblimit (profile/get-joblimit (:email account))]
        (if (>= (count (:email account)) joblimit)
          (f/fail "Job limit is reached, trash some to free slots")
          (f/attempt-all
           [r_lint (dockerlint path)
            r_mkdir (ssh "-i" (q config [:jenkins :key])
                         (ssh-host config) "mkdir" "-p" jobdir)
            r_scp (scp "-i" (q config [:jenkins :key])
                       path (str (ssh-host config) ":" jobdir "/Dockerfile"))
            r_job (sync_jobs config "-a" jobname)
            r_store (db/store! @jobs :jobid
                               {:jobid     jobname
                                :email     (:email account)
                                :account   (dissoc account :password :activation-link)
                                :lint      (if (.contains r_lint "is OK") true false)
                                :timestamp tstamp
                                :type      "vm_amd64_ascii"
                                :dockerfile (slurp path)})]
           {:lint r_lint
            :job  r_job}
           (f/when-failed [e]
             (f/fail (str "Job add '" jobname "' failure :: " (f/message e))))))
        (f/fail (str "Cannot find profile " (:email account) " :: "
                      (f/message joblimit)))))))
