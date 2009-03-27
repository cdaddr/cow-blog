(ns net.briancarper.util
  (:use clojure.contrib.str-utils)
  (:import (java.util Calendar)))

(defn key-vals [coll & keys]
  (doall (map #(get coll %) keys)))

(defn flat-map [coll]
  (apply concat (seq coll)))

(defn only-keys [h & keys]
  (apply hash-map
         (mapcat (fn [x] (when (get h x) [x (get h x)]))
                 keys)))

(defn glob [dirname]
  (into [] (.list (java.io.File. dirname))))

(defn rfc822-date [date]
  (let [f (java.text.SimpleDateFormat. "EEE', 'dd' 'MMM' 'yyyy' 'HH:mm:ss' 'Z")]
    (.format f date)))

(defn http-date [date]
  (let [f (java.text.SimpleDateFormat. "EE', 'dd MMM yyyy' 00:00:00 GMT'")]
    (.format f date)))

(defn format-date [date]
  (let [f (java.text.SimpleDateFormat. "MMM dd, yyyy")]
    (.format f date)))

(defn format-time [date]
  (let [f (java.text.SimpleDateFormat. "MMM dd, yyyy KK:mm a zz")]
    (.format f date)))

(defn sha-256 [s]
  (let [md (java.security.MessageDigest/getInstance "SHA-256")]
    (.update md (.getBytes s))
    (str-join ""
              (mapcat #(Integer/toHexString (bit-and 0xff %))
                      (into [] (.digest md))))))
