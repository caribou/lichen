(defproject antler/lichen "0.2.1"
  :description "A service for caching and retrieving images"
  :url "http://github.com/antler/lichen"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.clojure/clojure "1.3.0"]
                 [org.imgscalr/imgscalr-lib "4.2"]
                 [compojure "1.0.1"]
                 [ring/ring-core "1.1.0"
                  :exclusions [org.clojure/clojure
                               clj-stacktrace]]
                 [ring/ring-devel "1.1.0"]]
  :jvm-opts ["-agentlib:jdwp=transport=dt_socket,server=y,suspend=n"]
  :ring {:handler lichen.core/app
         :servlet-name "lichen"
         :init lichen.core/init
         :port 33113})
