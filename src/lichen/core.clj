(ns lichen.core
  (:require [clojure.string :as string]
            [clojure.java.io :as io]
            [lichen.image :as image]))

(def asset-root
  (or (System/getProperty "lichen.assets") "assets"))

(def lichen-root
  (or (System/getProperty "lichen.root") "lichen"))

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

(defn lichen-pattern
  [before & after]
  (re-pattern (apply str (concat [before lichen-root] after))))

(defn lichen-route?
  [uri]
  (re-find (lichen-pattern "^/") uri))

(defn remove-lichen-prefix
  [uri]
  (last (re-find (lichen-pattern "^/" "(/?.*)") uri)))

(defn extract-image-path
  [uri]
  (last (re-find (lichen-pattern "^/" "(.*/)[^/]*") uri)))

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
  [uri queries asset-root]
  (let [path (extract-image-path uri)
        token (build-token uri queries)
        extension (attain-extension uri)]
    (str asset-root path token extension)))

(defn wrap-lichen
  [handler asset-root]
  (fn [request]
    (if (lichen-route? (:uri request))
      (let [original (remove-lichen-prefix (:uri request))
            target (lichen-uri (:uri request) (:query-string request) asset-root)
            image-file (io/file target)]
        (if (not (cached-image? image-file))
          (do
            (println "resizing " (.getName image-file))
            (image/resize-file (pathify [asset-root original]) target (:params request))))
        {:status 200 :headers {} :body image-file})
      (handler request))))

(def lichen-handler
  (wrap-lichen (fn [request] "not found") asset-root))

