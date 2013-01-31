(ns lichen.image
  (:require [clojure.java.io :as io])
  (:import [java.awt.image BufferedImage]
           [java.awt Color]
           [javax.imageio IIOImage ImageIO ImageReader]
           [javax.imageio.plugins.jpeg JPEGImageWriteParam]
           [com.mortennobel.imagescaling ResampleOp]))

(defn open-image
  [filename]
  (let [image (ImageIO/read (io/file filename))
        width (.getWidth image)
        height (.getHeight image)
        buffered (BufferedImage. width height BufferedImage/TYPE_INT_RGB)
        graphics (.createGraphics buffered)]
    (.drawImage graphics image 0 0 width height Color/BLACK nil)
    buffered))

(defn output-image
  [image filename quality]
  (let [writer (.next (ImageIO/getImageWritersByFormatName "jpg"))
        output (ImageIO/createImageOutputStream (io/file filename))
        _ (.setOutput writer output)
        params (doto (.getDefaultWriteParam writer)
                 (.setCompressionMode JPEGImageWriteParam/MODE_EXPLICIT)
                 (.setCompressionQuality quality))]
    (.write writer nil (IIOImage. image nil nil) params)))

(defn resize-stream
  "Given an image stream, return a new stream that has been resized to the given width or height."
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
    (catch Exception e (println e))))
