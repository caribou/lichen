(ns lichen.image
  (:require [clojure.java.io :as io]
            [lichen.path :as path]
            [pantomime.mime :refer [mime-type-of]])
  (:import [java.awt.image BufferedImage]
           [java.awt Color]
           [javax.imageio IIOImage ImageIO ImageReader ImageWriteParam IIOException]
           [javax.imageio.plugins.jpeg JPEGImageWriteParam]
           [com.mortennobel.imagescaling ResampleOp]))

(defn open-stream
  [byte-format fill-color image]
  (let [width (.getWidth image)
        height (.getHeight image)
        mode byte-format
        buffered (BufferedImage. width height mode)
        graphics (.createGraphics buffered)]
    (.drawImage graphics image 0 0 width height fill-color nil)
    buffered))

(defn open-image-stream
  [image & [b+w]]
  (open-stream (if b+w BufferedImage/TYPE_BYTE_GRAY
                   BufferedImage/TYPE_4BYTE_ABGR)
               (Color. 0 0 0 0)
               image))

(defn open-jpeg-stream
  [image & [b+w]]
  (open-stream (if b+w BufferedImage/TYPE_BYTE_GRAY
                        BufferedImage/TYPE_3BYTE_BGR)
               Color/BLACK
               image))

(defn open-image
  [url & [b+w]]
  (try
    (let [stream (io/input-stream url)
          open (if (= (mime-type-of stream) "image/jpeg")
                 open-jpeg-stream
                 open-image-stream)]
      (open
       ;; FAILS for cmyk input - how do we fix this?
       (ImageIO/read (io/input-stream url))
       b+w))
    (catch javax.imageio.IIOException e
      (println "unsupported image for reading" url)
      :image-open-failure)
    (catch java.io.FileNotFoundException e
      (println "cannot find resource to open and resize")
      :not-found-failure)
    (catch java.lang.IllegalArgumentException e
      (println "cannot find resource to open and resize")
      :not-found-failure)))

(defn output-image-to
  [image stream quality extension target]
  (let [extension (or extension ".jpg")
        writer (.next (ImageIO/getImageWritersByFormatName extension))
        output (ImageIO/createImageOutputStream stream)
        _ (.setOutput writer output)
        params (.getDefaultWriteParam writer)]
    (cond (= "image/jpeg" (mime-type-of target))
          (do (.setCompressionMode params JPEGImageWriteParam/MODE_EXPLICIT)
              (.setCompressionQuality params quality))
          (get #{"gif"} extension)
               (.setCompressionMode params ImageWriteParam/MODE_EXPLICIT)
               :default nil)
    (.write writer nil (IIOImage. image nil nil) params)))

(defn output-image
  [image filename quality & [destination]]
  (let [filestream (io/file filename)]
    (output-image-to image filestream quality
                     (subs (path/attain-extension destination) 1)
                     destination)))

(defn resize-stream
  "Given an image stream, return a new stream that has been resized
   understood options:
     :b+w if true, render in black and white
     :width desired width
     :height desired height
     :quality desired image quality
     :scale scale dimensions by this factor (overrides :width and :height)"
  [original opts]
  (let [width (.getWidth original)
        height (.getHeight original)
        scale (if-let [r (get opts :scale)] (Double. r))
        desired-width (cond scale (* scale width)
                            (get opts :width) (Integer. (get opts :width))
                            :default nil)
        desired-height (cond scale (* scale height)
                             (get opts :height) (Integer. (get opts :height))
                             :default nil)
        ratio (/ (double width) (double height))
        target-width (or desired-width
                         (and desired-height (* desired-height ratio))
                         width)
        target-height (or desired-height
                          (and desired-width (/ desired-width ratio))
                          height)
        larger (max target-width target-height)
        resample (ResampleOp. target-width target-height)
        sized (.filter resample original nil)]
    sized))

(defn attempt-transformed-stream
  [source opts]
  (let [image-stream (open-image source (:b+w opts))]
    (case image-stream
      ;; if imageio cannot process it, just pass it along
      :image-open-failure [:copy (io/input-stream source)]
      :not-found-failure [:fail (println "cannot resize" source ", not found")]
      [:success (resize-stream image-stream opts)])))

(defn resize-file
  "Resizes the image specified by filename according to the supplied options
  (:width or :height), saving to file new-filename.  This function retains
  the aspect ratio of the original image."
  [filename new-filename opts]
  (try
    (let [extension (subs (path/attain-extension new-filename) 1)
          target new-filename
          content-type (or (:content-type opts)
                           (mime-type-of new-filename)
                           (mime-type-of filename))
          opts (assoc opts
                 :target new-filename
                 :extension extension
                 :content-type content-type)
          [success result] (attempt-transformed-stream filename opts)]
      (condp = success
        :success (output-image result new-filename
                               (Double. (or (:quality opts) 1.0))
                               new-filename)
        :copy (io/copy result (io/file new-filename))
        :fail nil))
    (catch Exception e (println e))))

(defn url-content-type
  [url]
  (-> url
      .openConnection
      (doto (.setRequestMethod "HEAD") .connect)
      .getContentType))

(defn resize-url
  "returns a stream suitable for passing to, for example, an s3 upload"
  [url opts]
  (try
    (let [content-type (or (:content-type opts)
                           (and (:target opts)
                                (mime-type-of (:target opts)))
                           "image/jpeg")
          extension (or (:extension opts)
                        (get {"image/jpeg" "jpg"
                              "image/png" "png"
                              "image/gif" "gif"} content-type "jpg"))
          target (or (:target opts) (str "placeholder" \. extension))
          opts (assoc opts
                 :target target
                 :extension extension
                 :content-type content-type)
          [success result] (attempt-transformed-stream url opts)]
      (if success
        (let [bytes (java.io.ByteArrayOutputStream.)]
          (output-image-to result bytes (or (:quality opts) 1.0) extension
                           target)
          (java.io.ByteArrayInputStream. (.toByteArray bytes)))
        result))
    (catch Exception e (println e) (.printStackTrace e))))
