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

(ns toaster.webpage
  (:require [clojure.java.io :as io]
            [clojure.data.json :as json]
            [clojure.data.csv :as csv]
            [yaml.core :as yaml]
            [toaster.config :as conf]
            [taoensso.timbre :as log]
            [failjure.core :as f]
            [toaster.ring :as ring]
            [hiccup.page :as page]
            [hiccup.form :as hf]))

(declare render)
(declare render-head)
(declare navbar-guest)
(declare navbar-account)
(declare render-footer)
(declare render-yaml)
(declare render-edn)
(declare render-error)
(declare render-error-page)
(declare render-static)
(declare render-error)

(defn q [req]
  "wrapper to retrieve parameters"
  ;; TODO: sanitise and check for irregular chars
  (get-in req (conj [:params] req)))

(defn button
  ([url text] (button url text [:p]))

  ([url text field] (button url text field "btn-secondary btn-lg"))

  ([url text field type]
   (hf/form-to [:post url]
               field ;; can be an hidden key/value field (project,
               ;; person, etc using hf/hidden-field)
               (hf/submit-button {:class (str "btn " type)} text))))

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


(defn reload-session [request]
  ;; TODO: validation of all data loaded via prismatic schema
  (conf/load-config "toaster" conf/default-settings)

  )

(defn render
  ([body]
  {:headers {"Content-Type"
             "text/html; charset=utf-8"}
   :body (page/html5
          (render-head)
          [:body ;; {:class "static"}
           navbar-guest
           [:div {:class "container-fluid"} body]
           (render-footer)])})
  ([account body]
   {:headers {"Content-Type"
              "text/html; charset=utf-8"}
    :body (page/html5
           (render-head)
           [:body (if (empty? account)
                    navbar-guest
                    navbar-account)
            [:div {:class "container-fluid"} body]
            (render-footer)])}))

(defn render-success
  "render a successful message without ending the page"
  [succ]
  [:div {:class "alert alert-success" :role "alert"}
   [:span {:class "far fa-check-circle"
           :aria-hidden "true" :style "padding: .5em"}]
   [:span {:class "sr-only"} "Success:" ]
   succ])

(defn render-error
  "render an error message without ending the page"
  [err]
  [:div {:class "alert alert-danger" :role "alert"}
   [:span {:class "far fa-meh"
           :aria-hidden "true" :style "padding: .5em"}]
   [:span {:class "sr-only"} "Error:" ]
   err])

(defn render-error-page
  ([]    (render-error-page {} "Unknown"))
  ([err] (render-error-page {} err))
  ([session error]
   (render
    [:div {:class "container-fluid"}
     (render-error error)
     (if-not (empty? session)
       [:div {:class "config"}
        [:h2 "Environment dump:"]
        (render-yaml session)])])))


(defn render-head
  ([] (render-head
       "toaster" ;; default title
       "toaster"
       "https://toaster.dyne.org")) ;; default desc

  ([title desc url]
   [:head [:meta {:charset "utf-8"}]
    [:meta {:http-equiv "X-UA-Compatible" :content "IE=edge,chrome=1"}]
    [:meta
     {:name "viewport"
      :content "width=device-width, initial-scale=1, maximum-scale=1"}]

    [:title title]

    ;; javascript scripts
    (page/include-js  "/static/js/jquery-3.2.1.min.js")
    (page/include-js  "/static/js/bootstrap.min.js")

    ;; cascade style sheets
    (page/include-css "/static/css/bootstrap.min.css")
    (page/include-css "/static/css/json-html.css")
    (page/include-css "/static/css/highlight-tomorrow.css")
    (page/include-css "/static/css/formatters-styles/html.css")
    (page/include-css "/static/css/formatters-styles/annotated.css")
    (page/include-css "/static/css/fa-regular.min.css")
    (page/include-css "/static/css/fontawesome.min.css")
    (page/include-css "/static/css/toaster.css")]))

(def navbar-brand
  [:div {:class "navbar-header"}
   [:button {:class "navbar-toggle" :type "button"
             :data-toggle "collapse"
             :data-target "#navbarResponsive"
             :aria-controls "navbarResponsive"
             :aria-expanded "false"
             :aria-label "Toggle navigation"}
    [:span {:class "sr-only"} "Toggle navigation"]
    [:span {:class "icon-bar"}]
    [:span {:class "icon-bar"}]
    [:span {:class "icon-bar"}]]
   [:a {:class "navbar-item " :href "/"}
    [:img {:src "/static/img/whale_toast.jpg"}]]])

(def navbar-guest
  [:nav
   {:class "navbar navbar-default navbar-fixed-top navbar-expand-md navbar-expand-lg"}
   navbar-brand
    [:div {:class "collapse navbar-collapse" :id "navbarResponsive"}
     [:ul {:class "nav navbar-nav hidden-sm md-auto ml-auto"}
      ;; --
      [:li {:class "divider" :role "separator"}]
      [:li {:class "nav-item"}
       [:a {:class "nav-link far fa-address-card"
            :href "/login"} " Login"]]
      ]]])

(def navbar-account
  [:nav {:class "navbar navbar-default navbar-fixed-top navbar-expand-md navbar-expand-lg"}
   navbar-brand
    [:div {:class "collapse navbar-collapse" :id "navbarResponsive"}
     [:ul {:class "nav navbar-nav hidden-sm ml-auto"}
      ;; --
      [:li {:class "divider" :role "separator"}]
      ;; LIST OF RELEVANT LINKS AFTER LOGIN
      [:li {:class "nav-item"}
       [:a {:class "nav-link far fa-address-card"
            :href "/list"} " List"]]
      ;; [:li {:class "nav-item"}
      ;;  [:a {:class "nav-link far fa-paper-plane"
      ;;       :href "/projects/list"} " Projects"]]
      [:li {:class "nav-item"}
       [:a {:class "nav-link far fa-plus-square"
            :href "/upload"} " Add"]]
      ;; [:li {:class "nav-item"}
      ;;  [:a {:class "nav-link far fa-save"
      ;;       :href "/reload"} " Reload"]]
      ;; --
      ;; [:li {:role "separator" :class "divider"} ]
      ;; [:li {:class "nav-item"}
      ;;  [:a {:class "nav-link far fa-file-code"
      ;;       :href "/config"} " Configuration"]]
      ]]])

(defn render-footer []
  [:footer {:class "row" :style "margin: 20px"}
   [:hr]

   [:div {:class "footer col-lg-3"}
    [:img {:src "/static/img/AGPLv3.png" :style "margin-top: 2.5em"
           :alt "Affero GPLv3 License"
           :title "Affero GPLv3 License"} ]]

   [:div {:class "footer col-lg-3"}
    [:a {:href "https://www.dyne.org"}
     [:img {:src "/static/img/swbydyne.png"
            :alt   "Software by Dyne.org"
            :title "Software by Dyne.org"}]]]
   ])


(defn render-static [body]
  (page/html5 (render-head)
              [:body {:class "fxc static"}

               navbar-guest

               [:div {:class "container"} body]

               (render-footer)
               ]))


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

;; (defonce readme
;;   (slurp (io/resource "public/static/README.html")))

(def login-form
  [:div
   [:h1 "Login for your  account"
    [:form {:action "/login"
            :method "post"}
     [:input {:type "text" :name "username"
              :placeholder "Username"
              :class "form-control"
              :style "margin-top: 1em"}]
     [:input {:type "password" :name "password"
              :placeholder "Password"
              :class "form-control"
              :style "margin-top: 1em"}]
     [:input {:type "submit" :value "Login"
              :class "btn btn-primary btn-lg btn-block"
              :style "margin-top: 1em"}]]]])

(def signup-form
  [:div
   [:h1 "Sign Up for a toaster account"
    [:form {:action "/signup"
            :method "post"}
     [:input {:type "text" :name "name"
              :placeholder "Name"
              :class "form-control"
              :style "margin-top: 1em"}]
     [:input {:type "text" :name "email"
              :placeholder "Email"
              :class "form-control"
              :style "margin-top: 1em"}]
     [:input {:type "password" :name "password"
              :placeholder "Password"
              :class "form-control"
              :style "margin-top: 1em"}]
     [:input {:type "password" :name "repeat-password"
              :placeholder "Repeat password"
              :class "form-control"
              :style "margin-top: 1em"}]
     [:input {:type "submit" :value "Sign In"
              :class "btn btn-primary btn-lg btn-block"
              :style "margin-top: 1em"}]]]])
