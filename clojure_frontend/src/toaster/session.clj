(ns toaster.session
  (:refer-clojure :exclude [get])
  (:require
   [toaster.config :as conf]
   [taoensso.timbre :as log]
   [failjure.core :as f]
   [just-auth.core :as auth]
   [hiccup.page :as page :refer [html5]]
   [clostache.parser :refer [render-resource]]
   [toaster.ring :as ring]))

(defn resource
  "renders a template, optionally passing it an hash-map of parameters."
  ([template] (resource template {}))
  ([template params] (render-resource template params)))

(defonce login  "templates/body_loginform.html")
(defonce signup "templates/body_signupform.html")
(defonce head   "templates/html_head.html")
(defonce footer "templates/body_footer.html")

(defn param [request param]
  (let [p (if (coll? param)
            (into [:params] param)
            (conj [:params] param))
        value (get-in request p)]
    (if (nil? value)
      (f/fail (str "Parameter not found: " p))
      value)))

;; TODO: not working?
(defn get [req arrk]
  {:pre (coll? arrk)}
  (if-let [value (get-in req (conj [:session] arrk))]
    value
    (f/fail (str "Value not found in session: " (str arrk)))))

(defn check-config [request]
  ;; reload configuration from file all the time if in debug mode
  (if-let [session (:session request)]
    (if (contains? session :config)
      (:config session)
      (conf/load-config "toaster" conf/default-settings))
    (f/fail "Session not found.")))

(defn check-account [request]
  ;; check if login is present in session
  (f/attempt-all
   [login (get-in request [:session :auth :email])
    user (auth/get-account @ring/auth login)]
   user
   (f/when-failed [e]
     (->> e f/message
          (str "Unauthorized access: ")
          f/fail))))

(defn check-database []
  (if-let [db @ring/db]
    db
    (f/fail "No connection to database.")))


(defn notify
  "render a notification message without ending the page"
  ([msg] (notify msg ""))
  ([msg class]
   ;; support also is-error (not included as notify class in bulma
   (let [tclass (if (= class "is-error") "is-danger" class)]
     (cond ;; log to console using timbre
       (= tclass "is-danger")  (log/error msg)
       (= tclass "is-warning") (log/warn msg)
       (= tclass "is-success") (log/info msg)
       (= tclass "is-primary") (log/debug msg))
     [:div {:class (str "notification " tclass " has-text-centered")} msg]
     )))
;; shortcut
(defn error
  ([msg] (notify msg "is-danger"))
  ([msg fail] (notify (str msg " :: " (f/message fail)) "is-danger")))

(defn render
  "render a full webpage using headers, navbar, body and footer"
  ([body] (render nil body))
  ([account body]
   {:headers {"Content-Type"
              "text/html; charset=utf-8"}
    :body  (page/html5
            (resource head)
            (conj body (resource footer)))}))

(defn render-template
  "render an html from resources using headers, navbar, body and footer"
  ([body] (render-template nil body))
  ([account body]
   {:headers {"Content-Type"
              "text/html; charset=utf-8"}
    :body (str "<!DOCTYPE html>\n<html>"
               (resource head)
               "\n<!-- body -->\n"
               (resource body)
               "\n<!-- end of body -->\n"
               (resource footer))}))

(defn render-html
  "render an html from resources using headers, navbar, body and footer"
  ([body] (render-template nil body))
  ([account body]
   {:headers {"Content-Type"
              "text/html; charset=utf-8"}
    :body (str "<!DOCTYPE html>\n<html>"
               (resource head)
               "\n<!-- body -->\n"
               body ;; html text string
               "\n<!-- end of body -->\n"
               (resource footer))}))

(defn render-error [err] (->> "is-danger" (notify err) render))
