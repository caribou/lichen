(ns lichen.image-test
  (:require [lichen.image :as image]
            [clojure.java.io :as io]
            [clojure.test :as test
             :refer [deftest testing is]]))

(defn memory-gripe
  []
  (println "TIME"
           (java.util.Date.)
           "MEMORY USAGE:"
           (/ (.totalMemory (java.lang.Runtime/getRuntime)) 1048576.0)))

(test/use-fixtures :once
  (fn [test]
    (test)
    (doseq [f ["test/test-resources/b+w-cmyk-jpg.jpg"
               "test/test-resources/b+w-rgb-jpg.jpg"
               "test/test-resources/b+w-test-png.png"
               "test/test-resources/b+w-test-gif.gif"
               "test/test-resources/logo.gif"]]
      (.delete (io/file f)))))

(def remote-image "http://www.google.com/images/srpr/logo4w.png")

(deftest readability
  (testing "can read a URL"
    (is (= true
           (image/can-read? (java.net.URL. remote-image)))))
  (testing "cannot read YUV"
    (is (= false
           (image/can-read? (io/file "test/test-resources/cmyk-jpg.jpg")))))
  (testing "can read url from string"
    (is (= true
           (image/can-read-url? remote-image))))
  (testing "can read file from string"
    (is (= true
           (image/can-read-file? "test/test-resources/rgb-jpg.jpg")))))

(deftest open-image
  (testing "cmyk jpeg handling"
    (is (= :image-open-failure
           (image/open-image (io/resource "test-resources/cmyk-jpg.jpg")))))
  (testing "nil input handling"
    (is (= :not-found-failure
           (image/open-image (io/resource "A THING THAT DOESN'T EXIST"))))))

(deftest attempt-transformed-stream
  (testing "cmyk jpeg handling"
    (is (= [clojure.lang.Keyword java.io.BufferedInputStream]
           (map type (image/attempt-transformed-stream
                      (io/resource "test-resources/cmyk-jpg.jpg")
                      {}))))))

(deftest resize-file
  (testing "cmyk jpeg handling"
    (is (= nil
           (image/resize-file "test/test-resources/cmyk-jpg.jpg"
                              "test/test-resources/b+w-cmyk-jpg.jpg"
                              {:b+w true})))
    (is (= java.io.BufferedInputStream
           (type (io/input-stream (io/file
                                   "test/test-resources/b+w-cmyk-jpg.jpg"))))))
  (testing "rgb jpeg handling"
    (is (= nil
           (image/resize-file "test/test-resources/rgb-jpg.jpg"
                              "test/test-resources/b+w-rgb-jpg.jpg"
                              {:b+w true})))
    (is (= java.io.BufferedInputStream
           (type (io/input-stream (io/file
                                   "test/test-resources/b+w-rgb-jpg.jpg"))))))
  (testing "png handling"
    (is (= nil
           (image/resize-file "test/test-resources/test-png.png"
                              "test/test-resources/b+w-test-png.png"
                              {:b+w true})))
    (is (= java.io.BufferedInputStream
           (type (io/input-stream (io/file
                                   "test/test-resources/b+w-test-png.png"))))))
  (testing "gif handling"
    (is (= nil
           (image/resize-file "test/test-resources/test-gif.gif"
                              "test/test-resources/b+w-test-gif.gif"
                              {:b+w true})))
    (is (= java.io.BufferedInputStream
           (type (io/input-stream (io/file
                                   "test/test-resources/b+w-test-gif.gif")))))))

(deftest resize-url
  (is (= nil
         (io/copy
          (image/resize-url
           (new java.net.URL remote-image)
           {:b+w true})
          (io/file "test/test-resources/logo.gif"))))
  (is (= java.io.BufferedInputStream
         (type (io/input-stream (io/file "test/test-resources/logo.gif"))))))
