;; Copyright (C) 2018 Dyne.org foundation

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

(ns toaster.views
  (:require
   [clojure.java.io :as io]
   [clojure.string :as str]
   [clojure.contrib.humanize :as humanize :refer [datetime]]
   ;;   [clojure.data.json :as json :refer [read-str]]
   [toaster.bulma :as web :refer [button notify render-yaml]]
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

(defn- box-list [account joblist]
  [:div {:class "box"}
   [:h1 {:class "title"} (str "List all toaster jobs for " (:name account))]
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
         ))]]])

(defn- box-add []
  [:div {:class "box"}
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

(defn dockerfile-upload-post [request config account]
  (let
      [tempfile (get-in request [:params :file :tempfile])
       filename (get-in request [:params :file :filename])
       params (:params request)]
    (cond
      (> (get-in params [:file :size]) 64000)
      ;; max upload size in bytes
      ;; TODO: put in config
      (f/fail "File too big in upload (64KB limit).")
      :else
      (let [file (io/copy tempfile (io/file "/tmp" filename))
            path (str "/tmp/" filename)]
        (io/delete-file tempfile)
        (if (not (.exists (io/file path)))
          (f/fail
           (str "Uploaded file not found: " filename))
          ;; file is now in 'tmp' var
          (f/if-let-ok? [newjob (job/add path config account)]
            [:div {:class "container"}
             [:h1 {:class "title"} "Job uploaded and added"]
             [:p "Log messages:"]
             (web/render-yaml newjob)]
            (web/notify (f/message newjob) "is-error")))))))

(defn dashboard
  ([account] (dashboard {} {} account))
  ([request config account]
  (f/attempt-all
   [joblist (db/query @ring/jobs {:email (:email account)})]
   [:div {:class "container has-text-centered"}

    [:span
     ;(if (> 0 (count joblist))
       (box-list account joblist)
       ;)
     (box-add) ]]
   (f/when-failed [e]
     (web/notify
      (str "Job list failure: " (f/message e)) "is-error")))))

(defn remove-job [request config account]
  (f/attempt-all
   [jobid (s/param request :jobid)
    jobfound (db/query @ring/jobs {:jobid jobid})
    r_rmjob (db/delete! @ring/jobs jobid)
    r_sync (job/sync_jobs config "-d" jobid)]
   (web/notify (str "Job removed: "  jobid) "is-primary")
   (f/when-failed [e]
     (web/notify (str "Failure removing job: " (f/message e)) "is-error"))))

(defn start-job [request config account]
  (f/attempt-all
   [jobid (s/param request :jobid)
    jobfound (db/query @ring/jobs {:jobid jobid})
    r_sync (job/sync_jobs config "-r" jobid)]
   (web/notify (str "Job started: " jobid) "is-success")
   (f/when-failed [e]
     (web/notify (str "Failure starting job: " (f/message e)) "is-error"))))

(defn view-job [request config account]
  (f/attempt-all
   [jobid (s/param request :jobid)
    jobfound (db/fetch @ring/jobs jobid)
    dockerfile (-> jobfound :dockerfile)]
   [:div {:class "box"}
    [:h1 {:class "title"} (str "Viewing job: " jobid)]
    [:pre dockerfile]]
   (f/when-failed [e]
     (web/notify (str "Failure viewing job: " (f/message e)) "is-error"))))
