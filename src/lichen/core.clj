(ns lichen.core
  (:require [clojure.string :as string]
            [clojure.java.io :as io]
            [clojure.walk :as walk]
            [aws.sdk.s3 :as s3]
            [lichen.path :as path]
            [lichen.image :as image]))

(defn cached-image?
  "has this disk asset been created?"
  [image-file]
  (.exists image-file))
  
(defn url-accessible?
  "has this remote asset been created?"
  [image-url]
  (try (.openStream image-url) true
       (catch java.io.IOException e false)))

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
  (let [dir (path/lichen-dir path asset-root)
        _ (.mkdirs (io/file dir))
        queries (path/query-string opts)
        target (path/lichen-uri path queries asset-root)
        image-file (io/file target)
        image-path (path/remove-lichen-prefix path)
        original (path/pathify [asset-root image-path])
        adapted (str asset-root (path/remove-lichen-prefix path))]
    (when-not (cached-image? image-file)
      (println "resizing" adapted "to" (.getName image-file))
      (image/resize-file adapted target (walk/keywordize-keys opts)))
    target))

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
  (let [[bucket path name extension] (path/analyze-s3-url input asset-root)
        queries  (path/query-string opts)
        [upload-key target] (path/lichen-s3-info bucket asset-root
                                            (str path name \. extension)
                                            extension queries)
        url (java.net.URL. input)
        content-type (image/url-content-type url)]
    (when-not (url-accessible? target)
      (try (s3/put-object creds bucket upload-key
                          (image/resize-url url opts content-type)
                          {:content-type (or content-type "image/jpeg")
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
    (if (path/lichen-route? (:uri request))
      (let [resize (lichen-resize (:uri request) (:query-params request) asset-root)]
        {:status 200 :headers {} :body (io/input-stream resize)})
      (handler request))))

(def lichen-handler
  (wrap-lichen (fn [request] "not found") path/asset-root))

