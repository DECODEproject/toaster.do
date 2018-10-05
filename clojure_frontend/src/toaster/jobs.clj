(ns toaster.jobs
  (:require
   [clojure.string :as str]
   [clojure.java.io :as io]
   [clj-time.core :as time]
   [clj-time.coerce :as tc]
   [failjure.core :as f]
   [taoensso.timbre :as log :refer [debug]]
   [me.raynes.conch :as sh :refer [with-programs]]
   [toaster.webpage :as web]
   [hiccup.form :as hf]))

(defn add [path config account]
  (with-programs [ssh scp node]
    (let [tstamp (tc/to-long (time/now))
          jobname (str (:email account) "-vm_amd64-" tstamp)
          jobdir  (str "/srv/toaster/" jobname)]
      (f/attempt-all
       [r_lint  (node "node_modules/dockerlint/bin/dockerlint.js" path)
        r_mkdir (ssh "-i" "../id_ed25519" "jenkins@sdk.bridge" "mkdir" "-p" jobdir )
        r_scp   (scp "-i" "../id_ed25519" path (str "jenkins@sdk.bridge:" jobdir))
        r_job   (ssh "-i" "../id_ed25519" "jenkins@sdk.bridge" "sync_jobs.py" "-a" jobname)]
       {:lint r_lint
        :job r_job}
       (f/when-failed [e]
         (web/render-error-page
          (str "Job add failure: " (f/message e))))))))
  

(defn listall [config account]
  (with-programs [ssh]
    (f/attempt-all
     [r_ssh   (ssh "-i" "../id_ed25519" "jenkins@sdk.bridge" "sync_jobs.py" "-l" (:email account))]
     (str/split r_ssh #"\n")
     (f/when-failed [e]
       (web/render-error-page
        (str "Job list failure: " (f/message e)))))))



