(ns lichen.core
  (:require [clojure.string :as string]
            [clojure.java.io :as io]
            [clojure.walk :as walk]
            [aws.sdk.s3 :as s3]
            [lichen.image :as image]))

(def asset-root
  "location under which images to be resized by lichen will be found"
  (or (System/getProperty "lichen.assets") "assets"))

(def lichen-root
  "location under which images lichen generates will be stored,
   also the routing under which lichen URIs are requested

   will be used as a literal path element and as part of a regex"
  (or (System/getProperty "lichen.root") "lichen"))

(def file-separator
  "file separator for constructing local lichen paths"
  (str (.get (java.lang.System/getProperties) "file.separator")))

(defn pathify
  "create a path for a local asset"
  [paths]
  (string/join file-separator paths))

(defn web-pathify
  "construct a path for an http asset"
  [paths]
  (string/join \/ paths))

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
  "returns everything after the last . in a file name"
  [uri]
  (last (re-find #".*(\..*)$" uri)))

(defn lichen-pattern
  "constructs a regular expression with lichen-root in the middle"
  [before & after]
  (re-pattern (apply str (concat [before lichen-root] after))))

(defn lichen-route?
  "determines if this URI should be served by lichen"
  [uri]
  (re-find (lichen-pattern "^/" "/[^?]+") uri))

(defn remove-lichen-prefix
  "removes the beginning portion of the URI matching lichen-pattern"
  [uri]
  (last (re-find (lichen-pattern "^/" "(/?.*)") uri)))

(defn extract-image-path
  "removes the beginning portion of URI matching lichen-pattern,
   and removes the filename portion of the URI. URI must be divided
   by / as per HTTP conventions"
  [uri]
  (last (re-find (lichen-pattern "^/" "(.*/)[^/]*") uri)))

(defn build-token
  "constructs an md5 hash of the URI and resizing options without (the
   potentially mutable) lichen-prefix"
  [uri queries]
  (let [clean-uri (remove-lichen-prefix uri)
        full-uri (str clean-uri "?" queries)
        hash (md5 full-uri)]
    hash))

(defn cached-image?
  "has this disk asset been created?"
  [image-file]
  (.exists image-file))
  
(defn url-accessible?
  "has this remote asset been created?"
  [image-url]
  (try (.openStream image-url) true
       (catch java.io.IOException e false)))

(defn lichen-dir
  "generates the directory location under which the resized image should be
   located, given a uri that points to the resizable image (which is stored
   locally on disk)"
  [uri asset-root]
  (let [path (extract-image-path uri)
        lichen-dir (str asset-root path lichen-root file-separator)]
    lichen-dir))

(defn lichen-uri
  "calculates the directory under which a resized version of the image pointed
   at by URI would live on the local file system"
  [uri queries asset-root]
  (let [dir (lichen-dir uri asset-root)
        token (build-token uri queries)
        extension (attain-extension uri)]
    (str dir token extension)))

(defn query-string
  "creates the canonical version of the query opts"
  [opts]
  (let [query (sort (seq opts))]
    (string/join "&" (map (fn [[k v]] (str (name k) "=" v)) query))))

(defn lichen-resize
  "path is the lichen url hit asking for the resized image

   opts are used to parameterize the resizing the image
   (see lichen.image/resize-stream)

   asset-root is the base under which the asset resized by this path lives, and
   the base location for the resized result

   it is assumed that the image to resize is on the local filesystem, and
   a new file, with a hash describing the resize options, is made in a lichen
   subdirectory under the image's parent"
  [path opts asset-root]
  (let [dir (lichen-dir path asset-root)
        _ (.mkdirs (io/file dir))
        queries (query-string opts)
        target (lichen-uri path queries asset-root)
        image-file (io/file target)
        image-path (remove-lichen-prefix path)
        original (pathify [asset-root image-path])
        adapted (str asset-root (remove-lichen-prefix path))]
    (when-not (cached-image? image-file)
      (println "resizing" adapted "to" (.getName image-file))
      (image/resize-file adapted target (walk/keywordize-keys opts)))
    target))

(defn re-quote
  "escape special chars from a URL to be used as part of a regular expression"
  [raw]
  (string/replace raw "." "\\."))

(defn analyze-s3-url
  "break an s3 URL down into constituant parts, abstracting out the asset root
   which allows rebasing under different roots without needlessly regenerating
   resized images"
  [urlstring asset-root]
  (let [protocol-match "^https?://"
        bucket-match "(.*)\\."
        host-match "s3.amazonaws.com/"
        asset-root-match (re-quote asset-root)
        dir-match "/(.*/)"
        name-match "(.*)\\."
        extension-match "(.+)$"
        analysis-re (re-pattern (str protocol-match
                                     bucket-match
                                     host-match
                                     asset-root-match
                                     dir-match
                                     name-match
                                     extension-match))
        [full bucket path name extension] (re-find analysis-re urlstring)]
    [bucket path name  extension]))


(defn lichen-s3-info
  [bucket asset-root path extension queries]
  (let [apropos-key (str (lichen-dir (str "/lichen/" path ) asset-root)
                         (build-token path queries)
                         \.
                         extension)]
    [apropos-key
     (java.net.URL. (str "http://" bucket ".s3.amazonaws.com/" 
                         apropos-key))]))

(defn get-http-object-size
  [urlstring]
  (-> urlstring
      java.net.URL.
      .openConnection
      .getContentLength
      (max 1)
      (doto println)))

(defn lichen-resize-s3
  "input is the URL (or URL string) where the file to be resized exists
   input must be in the same bucket that the resized image is to go into, and
   the creds supplied must provide access to upload a file to that bucket

   opts are used to parameterize the resizing the image
   (see lichen.image/resize-stream)

   An s3 key for upload will be generated based on the key, the hash of the
   options and key-name, and the key-name suffix"
  [input opts asset-root creds]
  (let [[bucket path name extension] (analyze-s3-url input asset-root)
        queries  (query-string opts)
        [upload-key target] (lichen-s3-info bucket asset-root
                                            (str path name \. extension)
                                            extension queries)]
    (when-not (url-accessible? target)
      (try (s3/put-object creds bucket upload-key
                          (image/resize-url (java.net.URL. input) opts)
                          {:content-type "image/jpeg"
                           ;; :content-length (get-http-object-size input)
                           }
                          (s3/grant :all-users :read))
           (catch Exception e
             (println "error in lichen-resize-s3")
             (.printStackTrace e))))
    target))

(defn wrap-lichen
  [handler asset-root]
  (fn [request]
    (if (lichen-route? (:uri request))
      (let [resize (lichen-resize (:uri request) (:query-params request) asset-root)]
        {:status 200 :headers {} :body (io/input-stream resize)})
      (handler request))))

(def lichen-handler
  (wrap-lichen (fn [request] "not found") asset-root))

