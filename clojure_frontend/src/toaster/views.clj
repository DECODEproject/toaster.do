(ns toaster.views
  (:require
   [clojure.java.io :as io]
   [clojure.string :as str]
   [clojure.contrib.humanize :as humanize :refer [datetime]]
   ;;   [clojure.data.json :as json :refer [read-str]]
   [toaster.bulma :as web]
   [toaster.session :as s]
   [toaster.ring :as ring]
   [toaster.jobs :as job]
   [failjure.core :as f]
   [auxiliary.string :refer [strcasecmp]]
   [toaster.config :as conf]
   [taoensso.timbre :as log :refer [debug]]
   [me.raynes.conch :as sh :refer [with-programs]]
   [clj-time.core :as time]
   [clj-time.coerce :as tc]
   [clj-time.local :as tl]
   [clj-storage.core :as db]
   [hiccup.form :as hf]))

(def dockerfile-upload-form
  [:div {:class "container"}
   [:h1 {:class "title"} "Upload a Dockerfile to toast"]
   [:p " Choose the file in your computer and click 'Submit' to
   proceed to validation."]
   [:form {:action  "dockerfile" :method "post"
           :class   "form-shell"
           :enctype "multipart/form-data"}
    [:div {:class "file has-label is-fullwidth"}
     [:label {:class "file-label"}
      [:input {:class "file-input" :name "file" :type "file"}]
      [:span {:class "file-cta"}
       [:span {:class "file-icon"}
        [:i {:class "fa fa-upload"}]]
       [:span {:class "file-label"}
        "Docker file..."]]
      [:span {:class "file-name"}]]]
    ;; [:fieldset {:class "fieldset-submit"}

    [:div {:class "field"}
     [:div {:class "control"}
      [:input {:class "button is-block is-info is-large is-fullwidth"
               :type "submit"
               :name  "submit" :value "submit"}]]]]])

(def dockerfile-edit-form
  [:div {:class "container"}
   [:h1 {:class "title"} "Edit your Dockerfile to toast"]
   [:div {:class "form-group"}
    [:form {:action  "dockerfile" :method "post"
            :class   "form-shell"
            :enctype "multipart/form-data"}
     [:fieldset {:class "fieldset btn btn-default btn-file btn-lg"}
      [:textarea {:name "editor" :id "editor"
                  :rows 30 :cols 72} "FROM: dyne/devuan:ascii"]
      [:input {:class "btn btn-primary btn-lg"
               :id    "field-submit" :type "submit"
               :name  "submit" :value "submit"}]]]]
   [:script "var editor = ace.edit(\"editor\");
            editor.setTheme(\"ace/theme/monokai\");
            editor.session.setMode(\"ace/mode/dockerfile\");"]])


(defn dockerfile-upload-post [request config account]
  (let
      [tempfile (get-in request [:params :file :tempfile])
       filename (get-in request [:params :file :filename])
       params (:params request)]
    (cond
      (> (get-in params [:file :size]) 64000)
      ;; max upload size in bytes
      ;; TODO: put in config
      (web/render-error params "File too big in upload (64KB limit).")
      :else
      (let [file (io/copy tempfile (io/file "/tmp" filename))
            path (str "/tmp/" filename)]
        (io/delete-file tempfile)
        (if (not (.exists (io/file path)))
          (web/render-error
           (log/spy :error
                    [:h1 (str "Uploaded file not found: " filename)]))
          ;; file is now in 'tmp' var
          [:div {:class "container"}
           [:h1 {:class "title"} "Job uploaded and added"]
           [:p "Log messages:"]
           (web/render-yaml (job/add path config account))])))))

(defn list-jobs [account]
  (f/attempt-all
   [joblist (db/query @ring/jobs {:email (:email account)})]
   [:div {:class "container has-text-centered"}
    [:h1 {:class "title"} (str "List all toaster jobs for " (:name account))]

    [:div {:class "box"}
     [:table {:class "table is-fullwidth is-hoverable"}
      [:thead nil
       [:tr nil [:th nil "Date"] [:th nil "Type"] [:th nil "Status"] [:th nil "Actions"]]]
      [:tbody nil
       (for [j joblist]
         (let [type (:type j)                               ;; (-> j (str/split #"-") second)
               tstamp (:timestamp j)                        ;; (-> j (str/split #"-") last)
               jobid (:jobid j)
               joburl (str/replace jobid #"@" "AT")]
           [:tr nil
            [:td {:class "date"} (-> tstamp Long/valueOf tc/from-long humanize/datetime)]
            [:td {:class "job"} [:a {:href (str "https://sdk.dyne.org:4443/view/web-sdk-builds/job/" joburl)} type]]
            [:td {:class "status"} [:a {:href (str "https://sdk.dyne.org:4443/job/" joburl "/lastBuild/console")}
                                    [:img {:src (str "https://sdk.dyne.org:4443/job/" joburl "/badge/icon")}]]]
            [:td {:class "actions"}
             [:div {:class "field is-grouped"}
              (web/button "/view" "\uD83D\uDC41"   (hf/hidden-field "jobid" jobid))
              (web/button "/start" "▶"             (hf/hidden-field "jobid" jobid))
              (web/button "/remove" "\uD83D\uDDD1" (hf/hidden-field "jobid" jobid))]]]
           ))]]]]
   (f/when-failed [e]
     (web/render-error
      (str "Job list failure: " (f/message e))))))


(defn remove-job [request config account]
  (f/attempt-all
   [jobid (s/param request :jobid)
    jobfound (db/query @ring/jobs {:jobid jobid})
    r_rmjob (db/delete! @ring/jobs jobid)
    r_sync (job/trash jobid config)]
   [:span
    [:div {:class "notification is-primary has-text-centered"}
     (str "Job removed: "  jobid)]
    (list-jobs account)]
   (f/when-failed [e]
     (web/render-error (str "Failure removing job: " (f/message e))))))

(defn start-job [request config account]
  (f/attempt-all
   [jobid (s/param request :jobid)
    jobfound (db/query @ring/jobs {:jobid jobid})
    r_sync (job/start jobid config)]
   [:div {:class "container"}
    [:h1 (str "Job started: " jobid)]
    [:p [:a {:href (str "https://sdk.dyne.org:4443/view/web-sdk-builds/job/"
                        (str/replace jobid #"@" "AT"))}]]]
   (f/when-failed [e]
     (web/render-error (str "Failure starting job: " (f/message e))))))

(defn view-job [request config account]
  (f/attempt-all
   [jobid (s/param request :jobid)
    jobfound (db/fetch @ring/jobs jobid)
    dockerfile (log/spy (-> jobfound :dockerfile))]
   [:div {:class "container"}
    [:h1 {:class "title"} (str "Viewing job: " jobid)]
    [:pre dockerfile]]
   (f/when-failed [e]
     (web/render-error (str "Failure viewing job: " (f/message e))))))
