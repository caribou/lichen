(defproject antler/lichen "0.3.1"
  :description "A service for caching and retrieving images"
  :url "http://github.com/antler/lichen"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.clojure/clojure "1.3.0"]
                 [com.mortennobel/java-image-scaling "0.8.5"]]
  :jvm-opts ["-agentlib:jdwp=transport=dt_socket,server=y,suspend=n"]
  :ring {:handler lichen.core/app
         :servlet-name "lichen"
         :init lichen.core/init
         :port 33113})
