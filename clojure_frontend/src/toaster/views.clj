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
   [toaster.bulma :as web :refer [button render-yaml]]
   [toaster.session :as s :refer [notify resource param]]
   [toaster.ring :as ring :refer [jobs]]
   [toaster.jobs :as job :refer [add sync_jobs]]
   [failjure.core :as f :refer [attempt-all when-failed if-let-ok?]]
   [auxiliary.string :refer [strcasecmp]]
   [toaster.config :refer [q]]
   [taoensso.timbre :as log]
   ;; [clj-time.core :as time]
   [clj-time.coerce :as tc]
   [clj-storage.core :as db]
   [hiccup.form :as hf]))

;; TODO: templated
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

(defn dockerfile-upload [request config account]
  (f/attempt-all
   [tempfile (s/param request [:file :tempfile])
    filename (s/param request [:file :filename])
    params (log/spy (:params request))]
   (if (> (s/param request [:file :size]) 64000)
     ;; TODO: put filesize limit in config
     (s/error "File too big in upload (64KB limit)")
     ;; else
     (let [file (io/copy tempfile (io/file "/tmp" filename))
           path (str "/tmp/" filename)]
       (io/delete-file tempfile)
       (if (not (.exists (io/file path)))
         (s/error (str "Uploaded file not found: " filename))
         ;; file is now in 'tmp' var
         (f/attempt-all
          [newjob (job/add path config account)]
          [:div {:class "container"}
           [:h1 {:class "title"} "Job uploaded and added"]
           [:p "Log messages:"]
           (web/render-yaml newjob)]
          ;; else when job/add is not-ok
          (f/when-failed [e]
            (s/error "Error adding job" e))
          ))))
   (f/when-failed [e]
     (s/error "Upload file error" e))))

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
      (s/resource "templates/body_addjob.html") ]]
    (f/when-failed [e]
      (s/error "Job list failure" e)))))

(defn remove-job [request config account]
  (f/attempt-all
   [jobid (s/param request :jobid)
    jobfound (db/query @ring/jobs {:jobid jobid})
    r_rmjob (db/delete! @ring/jobs jobid)
    r_sync (job/sync_jobs config "-d" jobid)]
   (s/notify (str "Job removed :: "  jobid) "is-primary")
   (f/when-failed [e]
     (s/error "Failure removing job" e))))

(defn start-job [request config account]
  (f/attempt-all
   [jobid (s/param request :jobid)
    jobfound (db/query @ring/jobs {:jobid jobid})
    r_sync (job/sync_jobs config "-r" jobid)]
   (s/notify (str "Job started: " jobid) "is-success")
   (f/when-failed [e]
     (s/error "Failure starting job" e))))

(defn view-job [request config account]
  (f/attempt-all
   [jobid (s/param request :jobid)
    jobfound (db/fetch @ring/jobs jobid)
    dockerfile (-> jobfound :dockerfile)]
   [:div {:class "box"}
    [:h1 {:class "title"} (str "Viewing job: " jobid)]
    [:form [:textarea {:id "code" :name "code" } dockerfile]]
    [:script "var editor = CodeMirror.fromTextArea(document.getElementById(\"code\"),
        { lineNumbers: true, mode: \"dockerfile\" });"]]
   (f/when-failed [e]
     (s/error "Failure viewing job" e))))
