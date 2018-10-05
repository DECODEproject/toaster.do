(ns toaster.jobs
  (:require
   [clojure.string :as str]
   [clojure.java.io :as io]
   [failjure.core :as f]
   [taoensso.timbre :as log :refer [debug]]
   [me.raynes.conch :as sh :refer [with-programs]]
   [toaster.webpage :as web]
   [hiccup.form :as hf]))

(defn add [path config account]
  (with-programs [ssh scp]
    (let [jobname "test@dyne.org-vm_amd64-12345678" ;; TODO:
          jobdir  (str "/srv/toaster/" jobname)]
      (f/attempt-all
       [r_mkdir (ssh "-i" "../id_ed25519" "jenkins@sdk.bridge" "mkdir" "-p" jobdir )
        r_scp   (scp "-i" "../id_ed25519" path (str "jenkins@sdk.bridge:" jobdir))
        r_ssh   (ssh "-i" "../id_ed25519" "jenkins@sdk.bridge" "sync_jobs.py" "-a" jobname {:verbose true})]
       (f/when-failed [e]
         (web/render-error-page
          (str "Job add failure: " (f/message e))))))))
  

(defn listall [config]
  (with-programs [ssh]
    (f/attempt-all
     [r_ssh   (ssh "-i" "../id_ed25519" "jenkins@sdk.bridge" "sync_jobs.py" "-l" "all" {:verbose true})]
     ;; (let [jobs (str/split (:stdout r_ssh) #" ")]
     ;;   jobs)
     (:stdout r_ssh)
     (f/when-failed [e]
       (web/render-error-page
        (str "Job list failure: " (f/message e)))))))



