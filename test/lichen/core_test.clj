(ns lichen.core-test
  (:use [clojure.test :only [testing is deftest]])
  (:require [clojure.test :as test]
            [lichen.core :as lichen]))

(deftest pathify
  (is (= "hello/world" (lichen/pathify ["hello" "world"]))))

(deftest attain-extension
  (is (= ".jpg" (lichen/attain-extension "this.is/a/dot.jpg"))))

(deftest lichen-pattern
  (is (= "madness?licheniscaribou"
         ;; regex is not equal to equivalent regex
         (str (lichen/lichen-pattern "madness?" "is" "caribou")))))

(deftest lichen-route?
  (is (lichen/lichen-route? "/lichen/test-image.jpg"))
  (comment "not changable at runtime :("
           (System/setProperty "lichen.root" "moss")
           (is (lichen/lichen-route? "/moss/test-image.jpg"))
           (System/setProperty "lichen.root" "lichen")))

(deftest remove-lichen-prefix
  (is (= "/test-image.jpg"
         (lichen/remove-lichen-prefix "/lichen/test-image.jpg"))))

(deftest extract-image-path
  (is (= "/0000/0001/"
         (lichen/extract-image-path "/lichen/0000/0001/test-image.jpg"))))

(deftest lichen-dir
  (is (= "assets/0000/0001/lichen/"
         (lichen/lichen-dir "/lichen/0000/0001/test-image.jpg" "assets"))))

(deftest lichen-uri
  (is (= "assets/0000/0001/lichen/df836c85e60e6ee311b43e7e925227e6.jpg"
         (lichen/lichen-uri "/lichen/0000/0001/test-image.jpg" "" "assets"))))
