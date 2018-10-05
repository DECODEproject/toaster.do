(ns toaster.views
  (:require
   [clojure.java.io :as io]
   ;;   [clojure.data.json :as json :refer [read-str]]
   [toaster.webpage :as web]
   [toaster.session :as s]
   [toaster.ring :as ring]
   [toaster.jobs :as job]
   [failjure.core :as f]
   [auxiliary.string :refer [strcasecmp]]
   [toaster.config :as conf]
   [taoensso.timbre :as log :refer [debug]]
   [me.raynes.conch :as sh :refer [with-programs]]
   [hiccup.form :as hf]))

(def dockerfile-upload-form
  [:div {:class "container-fluid"}
   [:h1 "Upload a Dockerfile to toast"]
   [:p " Choose the file in your computer and click 'Submit' to
proceed to validation."]
   [:div {:class "form-group"}
    [:form {:action "dockerfile" :method "post"
            :class "form-shell"
            :enctype "multipart/form-data"}
     [:fieldset {:class "fieldset btn btn-default btn-file btn-lg"}
      [:input {:name "file" :type "file"}]]
     ;; [:fieldset {:class "fieldset-submit"}
     [:input {:class "btn btn-primary btn-lg"
              :id "field-submit" :type "submit"
              :name "submit" :value "submit"}]]]])

(defn dockerfile-upload-post
  [request config account]
  (web/render
   (let
       [tempfile (get-in request [:params :file :tempfile])
        filename (get-in request [:params :file :filename])
        params   (:params request)]
     (cond
       (> (get-in params [:file :size]) 64000)
       ;; max upload size in bytes
       ;; TODO: put in config
       (web/render-error-page params "File too big in upload (64KB limit).")
       :else
       (let [file (io/copy tempfile (io/file "/tmp" filename))
             path (str "/tmp/" filename)]
         (io/delete-file tempfile)
         (if (not (.exists (io/file path)))
           (web/render-error-page
            (log/spy :error
                     [:h1 (str "Uploaded file not found: " filename)]))
           ;; file is now in 'tmp' var
           (with-programs [ssh scp]
             ;; (require '[clj-time.core :as time]
             ;;          '[clj-time.coerce :as tc])
             ;; timestamp: (tc/to-long (time/now))
             (let [jobname "test@dyne.org-vm_amd64-12345678" ;; TODO:
                   jobdir  (str "/srv/toaster/" jobname)]
               (log/spy (ssh "-i" "../id_ed25519" "jenkins@sdk.bridge" "mkdir" "-p" jobdir {:throw false}))
               (log/spy (scp "-i" "../id_ed25519" path (str "jenkins@sdk.bridge:" jobdir) {:throw false}))
               (log/spy (ssh "-i" "../id_ed25519" "jenkins@sdk.bridge" "sync_jobs.py" "-a" jobname {:verbose true :throw false}))))
                                                   
           ;; TODO: launch scripts
           ))))))
