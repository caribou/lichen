(ns lichen.image
  (:require [clojure.java.io :as io])
  (:import [java.awt.image BufferedImage]
           [java.awt Color]
           [javax.imageio IIOImage ImageIO ImageReader]
           [javax.imageio.plugins.jpeg JPEGImageWriteParam]
           [com.mortennobel.imagescaling ResampleOp]))

(defn open-image-stream
  [image]
  (let [width (.getWidth image)
        height (.getHeight image)
        buffered (BufferedImage. width height BufferedImage/TYPE_INT_RGB)
        graphics (.createGraphics buffered)]
    (.drawImage graphics image 0 0 width height Color/BLACK nil)
    buffered))

;; for file input, named for historical reasons
(defn open-image
  [filename]
  (open-image-stream (ImageIO/read (io/file filename))))

(defn open-image-url
  [url]
  (open-image-stream (ImageIO/read (io/input-stream url))))
        
(defn output-image-to
  [image stream quality]
  (let [writer (.next (ImageIO/getImageWritersByFormatName "jpg"))
        output (ImageIO/createImageOutputStream stream)
        _ (.setOutput writer output)
        params (doto (.getDefaultWriteParam writer)
                 (.setCompressionMode JPEGImageWriteParam/MODE_EXPLICIT)
                 (.setCompressionQuality quality))]
    (.write writer nil (IIOImage. image nil nil) params)))

(defn output-image
  [image filename quality]
  (let [filestream (io/file filename)]
    (output-image-to image filestream quality)))

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
        target-width (or desired-width (* desired-height ratio))
        target-height (or desired-height (/ desired-width ratio))
        larger (max target-width target-height)
        resample (ResampleOp. target-width target-height)
        sized (.filter resample original nil)]
    sized))

(defn resize-file
  "Resizes the image specified by filename according to the supplied options
  (:width or :height), saving to file new-filename.  This function retains
  the aspect ratio of the original image."
  [filename new-filename opts]
  (try
    (let [original (open-image filename)
          sized (resize-stream original opts)]
      (output-image sized new-filename (or (:quality opts) 1.0)))
    (catch Exception e (do (println e) (.printStackTrace e)))))

(defn resize-url
  "returns a stream suitable for passing to, for example, an s3 upload"
  [url opts]
  (try
    (let [original (open-image-url url)
          sized (resize-stream original opts)
          byte-stream (java.io.ByteArrayOutputStream.)]
      (output-image-to sized byte-stream (or (:quality opts) 1.0))
      (java.io.ByteArrayInputStream. (.toByteArray byte-stream)))
    (catch Exception e (do (println e)))))
