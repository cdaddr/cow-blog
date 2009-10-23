(ns blog.util
  (:use (compojure.html gen form-helpers)
        (clojure.contrib str-utils)))

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

(defn die [& xs]
  (throw (Exception. (apply str xs))))

(defn md5sum [s]
  (let [md5 (doto (java.security.MessageDigest/getInstance "MD5")
                (.update (.getBytes (str s))))]
    (apply str (map #(Integer/toHexString (bit-and 0xFF %))
                    (.digest md5)))))

(defn img
  ([src] [:img {:src src :alt src}])
  ([opts src] [:img (merge {:src src :alt src} opts)]))

