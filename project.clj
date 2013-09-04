(defproject antler/lichen "0.4.7-scale-ratio-2"
  :description "A service for caching and retrieving images"
  :url "http://github.com/antler/lichen"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.clojure/clojure "1.4.0"]
                 [com.mortennobel/java-image-scaling "0.8.5"]
                 ;; [org.imgscalr/imgscalr-lib "4.2"]
                 [clj-aws-s3 "0.3.6"]]
  :jvm-opts ["-agentlib:jdwp=transport=dt_socket,server=y,suspend=n"]
  :ring {:handler lichen.core/app
         :servlet-name "lichen"
         :init lichen.core/init
         :port 33113})
