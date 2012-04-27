(ns lichen.core
  (:use compojure.core
        [ring.middleware.file :only (wrap-file)]
        [ring.middleware.reload :only (wrap-reload)])
  (:require [clojure.string :as string]
            [clojure.java.io :as io]
            [compojure.route :as route]
            [compojure.handler :as handler]
            [lichen.image :as image]))

(def asset-root "assets")

(def file-separator
  (str (.get (java.lang.System/getProperties) "file.separator")))

(defn pathify
  [paths]
  (string/join file-separator paths))

(defn md5
  "Generate a md5 checksum for the given string"
  [token]
  (let [hash-bytes
        (doto (java.security.MessageDigest/getInstance "MD5")
          (.reset)
          (.update (.getBytes token)))]
    (.toString
     (new java.math.BigInteger 1 (.digest hash-bytes))
     16)))

(defn attain-extension
  [uri]
  (last (re-find #".*(\..*)$" uri)))

(defn lichen-route?
  [uri]
  (re-find #"^/lichen" uri))

(defn remove-lichen-prefix
  [uri]
  (last (re-find #"^/lichen(/?.*)" uri)))

(defn extract-image-path
  [uri]
  (last (re-find #"^/lichen(.*/)[^/]*" uri)))

(defn build-token
  [uri queries]
  (let [clean-uri (remove-lichen-prefix uri)
        full-uri (str clean-uri "?" queries)
        hash (md5 full-uri)]
    hash))

(defn cached-image?
  [image-file]
  (.exists image-file))
  
(defn lichen-uri
  [uri queries]
  (let [path (extract-image-path uri)
        token (build-token uri queries)
        extension (attain-extension uri)]
    (str asset-root path token extension)))

(defn lichen-handler
  [request]
  (if (lichen-route? (request :uri))
    (let [original (remove-lichen-prefix (request :uri))
          target (lichen-uri (request :uri) (request :query-string))
          image-file (io/file target)]
      (if (not (cached-image? image-file))
        (do
          (println "resizing " (.getName image-file))
          (image/resize-file (pathify [asset-root original]) target (request :params))))
      {:status 200 :headers {} :body image-file})))

(def app
  (-> lichen-handler
      (wrap-reload '[lichen.core])
      handler/site
      (wrap-file asset-root)))

(defn init
  [] )

