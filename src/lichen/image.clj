(ns lichen.image
  (:require [clojure.java.io :as io]
            [lichen.path :as path])
  (:import [java.awt.image BufferedImage]
           [java.awt Color]
           [javax.imageio IIOImage ImageIO ImageReader ImageWriteParam]
           [javax.imageio.plugins.jpeg JPEGImageWriteParam]
           [com.mortennobel.imagescaling ResampleOp]))

(defn open-image-stream
  [image & [b+w]]
  (let [width (.getWidth image)
        height (.getHeight image)
        mode (if b+w BufferedImage/TYPE_BYTE_GRAY
                 BufferedImage/TYPE_4BYTE_ABGR)
        buffered (BufferedImage. width height mode)
        graphics (.createGraphics buffered)]
    (.drawImage graphics image 0 0 width height (Color. 0 0 0 0) nil)
    buffered))

(defn open-jpeg-stream
  [image & [b+w]]
  (let [width (.getWidth image)
        height (.getHeight image)
        mode (if b+w BufferedImage/TYPE_BYTE_GRAY
                 BufferedImage/TYPE_3BYTE_BGR)
        buffered (BufferedImage. width height mode)
        graphics (.createGraphics buffered)]
    (.drawImage graphics image 0 0 width height Color/BLACK nil)
    buffered))

(defn open-image
  [url & [extension b+w]]
  (let [stream (io/input-stream url)
        open-stream (if (not (= extension "jpg"))
                      open-image-stream
                      open-jpeg-stream)]
    (open-stream
     ;; FAILS for cmyk input - how do we fix this?
     (ImageIO/read (io/input-stream url))
     b+w)))

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
  [source opts extension]
  (try
    [true
     (-> source
         (open-image extension (:b+w opts))
         (resize-stream opts))]
    (catch Exception e
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
    (catch Exception e (println e))))

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
