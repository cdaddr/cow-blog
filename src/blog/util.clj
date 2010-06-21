(ns blog.util
  (:require (clojure.contrib [string :as s])))

(defn die [& xs]
  (throw (Exception. (apply str xs))))

(defn safe-int [x]
  (if (number? x)
    x
    (when (not (empty? x))
      (Integer/parseInt x))))

(comment 
 (defn now []
   (.getTime (java.util.Calendar/getInstance)))

 (defn pretty-date [date]
   (.format (java.text.SimpleDateFormat. "MMM dd, yyyy")
            (now)))

 (defn rfc822-date [date]
   (let [f (java.text.SimpleDateFormat. "EEE', 'dd' 'MMM' 'yyyy' 'HH:mm:ss' 'Z")]
     (.format f date)))

 (defn timestamp []
   (pretty-date (now)))

 (defn form-row
   ([f name lab] (form-row f name lab nil))
   ([f name lab val]
      [:div
       (label name (str lab ":"))
       (f name val)]))

 (defn submit-row [lab]
   [:div.submit
    (submit-button lab)])

 (defn sha-256 [s]
   (let [md (java.security.MessageDigest/getInstance "SHA-256")]
     (.update md (.getBytes s))
     (str-join ""
               (mapcat #(Integer/toHexString (bit-and 0xff %))
                       (.digest md)))))


 (defn img
   ([src] [:img {:src src :alt src}])
   ([opts src] [:img (merge {:src src :alt src} opts)]))

)

(defn ensure-vec [x]
  (if (vector? x) x [x]))
