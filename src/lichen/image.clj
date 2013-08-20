(ns lichen.image
  (:require [clojure.java.io :as io]
            [lichen.path :as path])
  (:import [java.awt.image BufferedImage]
           [java.awt Color]
           [javax.imageio IIOImage ImageIO ImageReader ImageWriteParam]
           [javax.imageio.plugins.jpeg JPEGImageWriteParam]
           [com.mortennobel.imagescaling ResampleOp]))

(defn open-image-stream
  [image]
  (let [width (.getWidth image)
        height (.getHeight image)
        buffered (BufferedImage. width height BufferedImage/TYPE_4BYTE_ABGR)
        graphics (.createGraphics buffered)]
    (.drawImage graphics image 0 0 width height (Color. 0 0 0 0) nil)
    buffered))

(defn open-jpeg-stream
  [image]
  (let [width (.getWidth image)
        height (.getHeight image)
        buffered (BufferedImage. width height BufferedImage/TYPE_3BYTE_BGR)
        graphics (.createGraphics buffered)]
    (.drawImage graphics image 0 0 width height Color/BLACK nil)
    buffered))

(defn open-image
  [url & [extension]]
  ((if (not (= extension "jpg"))
     open-image-stream
     open-jpeg-stream)
   ;; FAILS for cmyk input - how do we fix this?
   (ImageIO/read (io/input-stream url))))

(defn output-image-to
  [image stream quality & [extension]]
  (let [extension (or extension "jpg")
        writer (.next (ImageIO/getImageWritersByFormatName extension))
        output (ImageIO/createImageOutputStream stream)
        _ (.setOutput writer output)
        params (.getDefaultWriteParam writer)]
    (cond (= extension "jpg")
          (.setCompressionMode params JPEGImageWriteParam/MODE_EXPLICIT)
          (= extension "gif")
          (.setCompressionMode params ImageWriteParam/MODE_EXPLICIT)
          :default nil)
    (when (#{"jpg"} extension)
      (.setCompressionQuality params quality))
    (.write writer nil (IIOImage. image nil nil) params)))

(defn output-image
  [image filename quality & [extension]]
  (let [filestream (io/file filename)]
    (output-image-to image filestream quality extension)))

(defn resize-stream
  "Given an image stream, return a new stream that has been resized
   understood options:
     :width desired width
     :height desired height
     :quality desired image quality"
  [original opts]
  (let [width (.getWidth original)
        height (.getHeight original)
        desired-width (if-let [w (opts :width)] (Integer. w))
        desired-height (if-let [h (opts :height)] (Integer. h))
        ratio (/ (float width) (float height))
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
  [source opts extension]
  (try
    [true
     (-> source
         (open-image extension)
         (resize-stream opts))]
    (catch Exception e
      (println e)
      (.printStackTrace e)
      (println "\nLICHEN.IMAGE USING ORIGINAL INPUT INSTEAD OF RESIZED\n")
      [false (io/input-stream source)])))
           
(defn resize-file
  "Resizes the image specified by filename according to the supplied options
  (:width or :height), saving to file new-filename.  This function retains
  the aspect ratio of the original image."
  [filename new-filename opts]
  (try
    (let [extension (subs (path/attain-extension filename) 1)
          [success result] (attempt-transformed-stream filename opts extension)]
      (if success
        (output-image result new-filename (or (:quality opts) 1.0) extension)
        (io/copy result (io/file new-filename))))
    (catch Exception e (println (format "resizing:  No file by the name %s" filename)))))

(defn url-content-type
  [url]
  (-> url
      .openConnection
      (doto (.setRequestMethod "HEAD") .connect)
      .getContentType))

(defn resize-url
  "returns a stream suitable for passing to, for example, an s3 upload"
  [url opts & [content-type]]
  (try
    (let [content-type (or content-type (url-content-type url))
          extension (get {"image/jpeg" "jpg"
                          "image/png" "png"
                          "image/gif" "gif"} content-type "jpg")
          [success result] (attempt-transformed-stream url opts extension)]
      (if success
        (let [bytes (java.io.ByteArrayOutputStream.)]
          (output-image-to result bytes (or (:quality opts) 1.0) extension)
          (java.io.ByteArrayInputStream. (.toByteArray bytes)))
        result))
    (catch Exception e (println e) (.printStackTrace e))))
