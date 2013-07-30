(ns lichen.core-test
  (:require [clojure.test :as test :refer [testing is deftest]]
            [lichen.core :as lichen]
            [lichen.path :as path]))

(deftest pathify
  (is (= "hello/world" (path/pathify ["hello" "world"]))))

(deftest attain-extension
  (is (= ".jpg" (path/attain-extension "this.is/a/dot.jpg"))))

(deftest lichen-pattern
  (is (= "madness?licheniscaribou"
         ;; regex is not equal to equivalent regex
         (str (path/lichen-pattern "madness?" "is" "caribou")))))

(deftest lichen-route?
  (is (path/lichen-route? "/lichen/test-image.jpg"))
  (comment "not changable at runtime :("
           (System/setProperty "lichen.root" "moss")
           (is (path/lichen-route? "/moss/test-image.jpg"))
           (System/setProperty "lichen.root" "lichen")))

(deftest remove-lichen-prefix
  (is (= "/test-image.jpg"
         (path/remove-lichen-prefix "/lichen/test-image.jpg"))))

(deftest extract-image-path
  (is (= "/0000/0001/"
         (path/extract-image-path "/lichen/0000/0001/test-image.jpg"))))

(deftest lichen-dir
  (is (= "assets/0000/0001/lichen/"
         (path/lichen-dir "/lichen/0000/0001/test-image.jpg" "assets"))))

(deftest lichen-uri
  (is (= "assets/0000/0001/lichen/df836c85e60e6ee311b43e7e925227e6.jpg"
         (path/lichen-uri "/lichen/0000/0001/test-image.jpg" "" "assets"))))

(deftest url-accessible?
  (is (lichen/url-accessible? (java.net.URL. "http://www.example.com")))
  (is (not (lichen/url-accessible?
            (java.net.URL. "http:// A SILLY URL YOU CANNOT OPEN ☃☃☃")))))

(deftest query-string
  (is (= (path/query-string {:width 100 :height 100 :quality 0.8})
         "height=100&quality=0.8&width=100")))

(deftest re-quote
  (is (= (path/re-quote "waiting...")
         "waiting\\.\\.\\.")))

(deftest analyze-s3-url
  (is (= (path/analyze-s3-url "http://a.s3.amazonaws.com/x/y/z.jpg" "x")
         ["a" "y/" "z" "jpg"])))

(deftest lichen-s3-info
  (is (= (path/lichen-s3-info
          "the.vestibule" "zinio" "assets/0000/0077/zinio-news-hero-web-50.jpg"
          "jpg" {:width 100 :height 100 :quality 0.8})
         ["zinio/assets/0000/0077/lichen/1f0992e142ed341ab2c760c723e365ac.jpg"
          (java.net.URL.
           "http://the.vestibule.s3.amazonaws.com/zinio/assets/0000/0077/lichen/1f0992e142ed341ab2c760c723e365ac.jpg")])))
