;; Copyright (C) 2015-2018 Dyne.org foundation

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

(ns toaster.bulma
  (:require [clojure.java.io :as io]
            [clojure.data.csv :as csv]
            [taoensso.timbre :as log]
            [yaml.core :as yaml]
            [hiccup.page :as page]
            [hiccup.form :as hf]))

(defn button
  ([url text] (button url text [:p]))

  ([url text field] (button url text field "button"))

  ([url text field type]
   (hf/form-to [:post url]
               field ;; can be an hidden key/value field (project,
               ;; person, etc using hf/hidden-field)
               [:p {:class "control"}
                (hf/submit-button {:class (str "button " type)} text)])))

(defn button-cancel-submit [argmap]
  [:div
   {:class
    (str "row col-md-6 btn-group btn-group-lg "
         (:btn-group-class argmap))
    :role "group"}
   (button
    (:cancel-url argmap) "Cancel"
    (:cancel-params argmap)
    "btn-primary btn-lg btn-danger col-md-3")
   (button
    (:submit-url argmap) "Submit"
    (:submit-params argmap)
    "btn-primary btn-lg btn-success col-md-3")])

;; highlight functions do no conversion, take the format they highlight
;; render functions take edn and convert to the highlight format
;; download functions all take an edn and convert it in target format
;; edit functions all take an edn and present an editor in the target format


(defn render-yaml
  "renders an edn into an highlighted yaml"
  [data]
  [:span
   [:pre [:code {:class "yaml"}
          (yaml/generate-string data)]]
   [:script "hljs.initHighlightingOnLoad();"]])

(defn highlight-yaml
  "renders a yaml text in highlighted html"
  [data]
  [:span
   [:pre [:code {:class "yaml"}
          data]]
   [:script "hljs.initHighlightingOnLoad();"]])


(defn highlight-json
  "renders a json text in highlighted html"
  [data]
  [:span
   [:pre [:code {:class "json"}
          data]]
   [:script "hljs.initHighlightingOnLoad();"]])

(defn download-csv
  "takes an edn, returns a csv plain/text for download"
  [data]
  {:headers {"Content-Type"
             "text/plain; charset=utf-8"}
   :body (with-out-str (csv/write-csv *out* data))})

(defn edit-edn
  "renders an editor for the edn in yaml format"
  [data]
  [:div;; {:class "form-group"}
   [:textarea {:class "form-control"
               :rows "20" :data-editor "yaml"
               :id "config" :name "editor"}
    (yaml/generate-string data)]
   [:script {:src "/static/js/ace.js"
             :type "text/javascript" :charset "utf-8"}]
   [:script {:type "text/javascript"}
    (slurp (io/resource "public/static/js/ace-embed.js"))]
   ;; code to embed the ace editor on all elements in page
   ;; that contain the attribute "data-editor" set to the
   ;; mode language of choice
   [:input {:class "btn btn-success btn-lg pull-top"
            :type "submit" :value "submit"}]])

(def brand-img "/static/img/cafudda.jpg")
