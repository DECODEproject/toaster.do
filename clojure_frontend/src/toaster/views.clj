(ns toaster.views
  (:require
    [clojure.java.io :as io]
    [clojure.string :as str]
    [clojure.contrib.humanize :as humanize :refer [datetime]]
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
    [clj-time.core :as time]
    [clj-time.coerce :as tc]
    [clj-time.local :as tl]
    [hiccup.form :as hf]))

(def dockerfile-upload-form
  [:div {:class "container-fluid"}
   [:h1 "Upload a Dockerfile to toast"]
   [:p " Choose the file in your computer and click 'Submit' to
proceed to validation."]
   [:div {:class "form-group"}
    [:form {:action  "dockerfile" :method "post"
            :class   "form-shell"
            :enctype "multipart/form-data"}
     [:fieldset {:class "fieldset btn btn-default btn-file btn-lg"}
      [:input {:name "file" :type "file"}]]
     ;; [:fieldset {:class "fieldset-submit"}
     [:input {:class "btn btn-primary btn-lg"
              :id    "field-submit" :type "submit"
              :name  "submit" :value "submit"}]]]])

(def dockerfile-edit-form
  [:div {:class "container-fluid"}
   [:h1 "Edit your Dockerfile to toast"]
   [:div {:class "form-group"}
    [:form {:action  "dockerfile" :method "post"
            :class   "form-shell"
            :enctype "multipart/form-data"}
     [:fieldset {:class "fieldset btn btn-default btn-file btn-lg"}
      [:textarea {:name "editor" :id "editor"
                  :rows 30 :cols 72 } "FROM: dyne/devuan:ascii"]
      [:input {:class "btn btn-primary btn-lg"
               :id    "field-submit" :type "submit"
               :name  "submit" :value "submit"}]]]]
   [:script "var editor = ace.edit(\"editor\");
      editor.setTheme(\"ace/theme/monokai\");
      editor.session.setMode(\"ace/mode/dockerfile\");"]])



(def welcome-menu
  [:div {:class "container-fluid"}
   [:div {:class "row-fluid"}
    [:div {:class "row"} [:a {:href "/list"} "List all your toaster jobs"]]
    [:div {:class "row"} [:a {:href "/upload"} "Upload a new toaster job"]]]])

(defn dockerfile-upload-post [request config account]
  (web/render
    account
    (let
      [tempfile (get-in request [:params :file :tempfile])
       filename (get-in request [:params :file :filename])
       params (:params request)]
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
            [:div {:class "container-fluid"}
             [:h1 "Job uploaded and added"]
             [:p "Log messages:"]
             (web/render-yaml (job/add path config account))]))))))

(defn list-jobs [request config account]
  (f/attempt-all
    [joblist (job/listall config account)]
    (web/render
      account
      [:div {:class "container-fluid"}
       [:h1 (str "List all toaster jobs for " (:name account))]
       [:table {:class "sortable table"}
        [:thead nil
         [:tr nil [:th nil "Date"] [:th nil "Type"] [:th nil "Actions"]]]
        [:tbody nil
         (for [j joblist]
           (let [type (-> j (str/split #"-") second)
                 tstamp (-> j (str/split #"-") last)]
             [:tr nil
              [:td {:class "date"} (-> tstamp Long/valueOf tc/from-long tl/to-local-date-time
                                       humanize/datetime)]
              [:td {:class "job"} [:a {:href (str "https://sdk.dyne.org:4443/view/web-sdk-builds/job/"
                                                  (str/replace j #"@" "AT"))} type]]
              [:td {:class "start-job"} (web/button "/start" "Start" (hf/hidden-field "job" j))
               (web/button "/remove" "Remove" (hf/hidden-field "job" j))]]
             ))]]])
    (f/when-failed [e]
                   (web/render-error-page
                     (str "Job list failure: " (f/message e))))
    ))
