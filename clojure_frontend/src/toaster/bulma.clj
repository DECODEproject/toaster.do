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
            [yaml.core :as yaml]
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


;; TODO: navbars for bulma
(def navbar-brand   [:div {:class "navbar"}])
(def navbar-guest   [:div {:class "navbar"}])
(def navbar-account [:div {:class "navbar"}])

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

(defn render
  ([body]
   {:headers {"Content-Type"
              "text/html; charset=utf-8"}
    :body (page/html5
           (render-head)
           [:body ;; {:class "static"}
            ;; navbar-guest
            body
            (render-footer)])})
  ([account body]
   {:headers {"Content-Type"
              "text/html; charset=utf-8"}
    :body (page/html5
           (render-head)
           [:body ;; (if (empty? account)
                  ;;   navbar-guest
                  ;;   navbar-account)
            body
            (render-footer)])}))

(defn render-success
  "render a successful message without ending the page"
  [succ]
  [:div {:class "notification is-primary"} succ])

(defn render-error
  "render an error message without ending the page"
  [err]
  [:div {:class "notification is-danger"} err])

(defn render-error-page
  ([]    (render-error-page {} "Unknown"))
  ([err] (render-error-page {} err))
  ([session error]
   (render
    [:div {:class "container"}
     (render-error error)])))


(defn render-head
  ([] (render-head
       "toaster.do" ;; default title
       "From Docker to VM or ARM SDcard in a few clicks, powered by Devuan and the DECODE project"       "https://toaster.dyne.org")) ;; default desc

  ([title desc url]
   [:head [:meta {:charset "utf-8"}]
    [:meta {:http-equiv "X-UA-Compatible" :content "IE=edge,chrome=1"}]
    [:meta
     {:name "viewport"
      :content "width=device-width, initial-scale=1"}]

    [:title title]
    [:link {:href "https://fonts.googleapis.com/css?family=Open+Sans:300,400,700"
            :rel "stylesheet"}]
    ;; cascade style sheets
    (page/include-css "/static/css/bulma.min.css")
    (page/include-css "/static/css/json-html.css")
    ;;    (page/include-css "/static/css/fa-regular.min.css")
    ;;    (page/include-css "/static/css/fontawesome.min.css")

    (page/include-css "https://maxcdn.bootstrapcdn.com/font-awesome/4.7.0/css/font-awesome.min.css")
    (page/include-css "/static/css/toaster.css")]))

(defn render-footer []
  [:footer {:class "footer"}
   [:div {:class "content has-text-centered"}
    [:p {:class "has-text-grey"} "toaster.do transforms your Docker prototype into "
     "an installable " [:a {:href "https://devuan.org"} "Devuan GNU+Linux"]
     " image, choose any supported target architecture!"]
    [:div {:class "columns is-variable is-8"}
     [:div {:class "column is-one-quarter is-narrow-touch"}
      [:a {:href "https://www.dyne.org"}
       [:figure {:class "image"}
        [:img {:src "/static/img/swbydyne.png"
               :alt   "Software by Dyne.org"
               :title "Software by Dyne.org"}]]]]
     [:div {:class "column auto"}]
     [:div {:class "column is-one-quarter"}
      [:figure {:class "image"}
       [:img {:src "/static/img/AGPLv3.png" ;; :style "margin-top: 2.5em"
              :alt "Affero GPLv3 License"
              :title "Affero GPLv3 License"}]]]]]])

(defn render-static [body]
  (page/html5 (render-head)
              [:body {:class "fxc static"}

               ;; navbar-guest

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

(def brand-img "/static/img/whale_toast.jpg")
(defn- hero-login-box [body]
  [:section {:class "hero is-fullheight"}
   [:div {:class "hero-body"}
    [:div {:class "container has-text-centered"}
     body
     ]]])

(def login-form  
  (hero-login-box 
   [:div {:class "column is-4 is-offset-4"}
    [:h3 {:class "title has-text-grey"} "toaster.do"]
    [:h4 {:class "subtitle has-text-grey"}
     "from Docker to VM in a few clicks, powered by "
     [:a {:href "https://decodeproject.eu"} "DECODEproject.EU"]]
    [:p {:class "subtitle has-text-grey"}
     "please login to operate"]
    [:div {:class "box"}
     [:figure {:class "avatar"}
      [:img {:src brand-img }]]
     [:form {:action "/login"
             :method "post"}
      [:div {:class "field"}
       ;;         [:label {:class "label is-large"} "Email"]
       [:div {:class "control has-icons-left"}
        [:input {:type "email" :name "username"
                 :placeholder "Email"
                 :class "input is-large"}]
        [:span {:class "icon is-small is-left"}
         [:i {:class "fa fa-envelope fa-xs"}]]]]
      [:div {:class "field"}
       ;;         [:label {:class "label is-large"} "Password"]
       [:div {:class "control has-icons-left"}
        [:input {:type "password" :name "password"
                 :placeholder "Password"
                 :class "input is-large"}]
        [:span {:class "icon is-small is-left"}
         [:i {:class "fa fa-lock fa-xs"}]]]]
      [:div {:class "field"}
       [:p {:class "control"}
        [:input {:type "submit" :value "Login"
                 :class "button is-block is-info is-large has-icons-left is-fullwidth"}]]]]]
    [:p {:class "subtitle has-text-grey"}
     "...or " [:a {:href "/signup"} "signup for a new account"]]]))

(def signup-form
  (hero-login-box
   [:div {:class "column is-4 is-offset-4"}
    [:h3 {:class "title has-text-grey"} "toaster.do"]
    [:h4 {:class "subtitle has-text-grey"}
     "sign up for a new account"]
;;     [:p {:class "subtitle has-text-grey"}]
    [:div {:class "box"}
     [:figure {:class "avatar"}
      [:img {:src brand-img}]]
     [:form {:action "/signup"
             :method "post"}
      [:div {:class "field"}
       [:div {:class "control"}
        [:input {:type "text" :name "name"
                 :placeholder "Name"
                 :class "input"}]]]
      [:div {:class "field"}
       [:div {:class "control"}
        [:input {:type "email" :name "email"
                 :placeholder "Email"
                 :class "input"}]]]
      [:div {:class "field"}
       [:div {:class "control"}
        [:input {:type "password" :name "password"
                 :placeholder "Password"
                 :class "input"}]]]
      [:div {:class "field"}
       [:div {:class "control"}
        [:input {:type "password" :name "repeat-password"
                 :placeholder "Repeat password"
                 :class "input"}]]]
      [:div {:class "field"}
       [:div {:class "control"}
        [:input {:type "submit" :value "Sign In"
                 :class "button is-block is-info is-large is-fullwidth"}]]]]]]))
