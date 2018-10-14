(ns toaster.session
  (:refer-clojure :exclude [get])
  (:require
   [clojure.java.io :as io]
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

(defn fail [msg err] (f/fail (str msg " :: " (f/message err))))

(defn upload
  "manages the upload of a file and calls a function with its path.
  (callback) is called with 3 args: path, config and account"
  [request config account callback]
  (f/attempt-all
   [tempfile (param request [:file :tempfile])
    filename (param request [:file :filename])
    filesize (param request [:file :size])]
   (if (> filesize 64000)
     ;; TODO: put filesize limit in config
     (f/fail "file too big to upload (64KB limit)")
     ;; else
     (let [file (io/copy tempfile (io/file "/tmp" filename))
           path (str "/tmp/" filename)]
       (io/delete-file tempfile)
       (if (not (.exists (io/file path)))
         (f/fail (str "uploaded file not found: " filename))
         ;; file is now in 'tmp' var
         (callback path config account))))
   (f/when-failed [e]
     (error "Upload file error" e))))

(defn adduser
  "manages the creation of a user (pending activation) and calls a fun callback.
  (fun) takes 2 args: the name and email of the user."
  [request fun]
  (f/attempt-all
   [name (param request :name)
    email (param request :email)
    password (param request :password)
    repeat-password (param request :repeat-password)
    activation {:activation-uri
                (get-in request [:headers "host"])}]

   (if (not= password repeat-password)
     (f/fail "repeated password did not match")
     (f/try*
      (f/attempt-all
       [signup (auth/sign-up @ring/auth
                             name
                             email
                             password
                             activation
                             [])]
       (fun name email)
       (f/when-failed [e]
         (fail (str "failure creating account '"email"'") e)))))
   (f/when-failed [e]
     (render
      [:body
       (error "Sign-up failure" e)
       (resource signup)]))))
