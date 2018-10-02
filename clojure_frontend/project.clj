(defproject toaster "0.1.0-SNAPSHOT"
  :description "Basic compojure based authenticated website"
  :url "http://dyne.org"
  :min-lein-version "2.0.0"
  :dependencies [[org.clojure/clojure "1.9.0"]
                 [org.clojure/data.csv "0.1.4"]
                 [compojure "1.6.1"]
                 [ring/ring-defaults "0.3.2"]
                 [ring-middleware-accept "2.0.3"]
                 ;; mustache templates
                 [de.ubercode.clostache/clostache "1.4.0"]
                 ;; error handling
                 [failjure "1.3.0"]
                 ;; logging done right with timbre
                 [com.taoensso/timbre "4.10.0"]
                 ;; authentication library
                 [org.clojars.dyne/just-auth "0.4.0"]
                 ;; web forms made easy
                 [formidable "0.1.10"]
                 ;; parsing configs if any
                 [io.forward/yaml "1.0.9"]
                 ;; Data validation
                 [prismatic/schema "1.1.9"]
                 ;; filesystem utilities
                 [me.raynes/fs "1.4.6"]
                 ;; time from joda-time
                 [clj-time "0.14.4"]]
  :aliases {"test" "midje"}
  :source-paths ["src"]
  :resource-paths ["resources"]
  :plugins [[lein-ring "0.12.4"]]
  :ring    {:init toaster.ring/init
            :handler toaster.handler/app}
  :uberwar {:init toaster.ring/init
            :handler toaster.handler/app}
  :mail toaster.handler
  :profiles { :dev {:dependencies [[javax.servlet/servlet-api "2.5"]
                                   [ring/ring-mock "0.3.2"]
                                   [midje "1.9.2"]]
                    :plugins [[lein-midje "3.1.3"]]
                    :aot :all
                    :main toaster.handler}
             :uberjar {:aot  :all
                       :main toaster.handler}}
  )
