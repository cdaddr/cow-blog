(ns blog.tokyocabinet
  (:import (tokyocabinet HDB)))

(declare DB)

(defn- bit-or* [& xs]
  (reduce bit-or xs))

(defmacro with-db [file & body]
  `(binding [DB (.newInstance HDB)]
     (try
      (.open DB ~file (bit-or* HDB/OWRITER HDB/OCREAT HDB/OTSYNC))
      ~@body
      (finally (.close DB)))))

(defmethod clojure.core/print-dup java.util.Date [o w]
  (.write w (str "#=" `(java.util.Date. ~(.getTime o)))))

(defn store [key val]
  (binding [*print-dup* true]
    (.put DB (str key) (pr-str val))))

(defn fetch [key]
  (when-let [s (.get DB (str key))]
    (read-string s)))

(defn delete [key]
  (.out DB key))

(defn fetch-keys []
  (if (.iterinit DB)
    (doall (take-while identity
                       (repeatedly #(.iternext2 DB))))
    (throw (Exception. "Failed to acquire iterator."))))

(defn fetch-keyvals []
  (let [keys (fetch-keys)]
   (doall
    (zipmap keys 
            (map fetch keys)))))

(defn- persist
  "Persists a ref to disk.  Slurps the whole DB into memory, compares all the keys, and stores any keys that have been changed.  This is very inefficient and can be improved, but it's fast enough for my blog."
  [a data]
  (with-db (:file ^data)
    (let [on-disk (fetch-keyvals)]
      (doseq [[k _] on-disk]
        (when-not (@data k)
          (delete k)))
      (doseq [[k v] @data]
        (when-not (= v (on-disk k))
          (store k v))))))

(defn make-dataref
  "Make a ref with a watcher agent that will persist any changes to the ref to disk via Tokyo Cabinet."
  [file]
  (let [watcher (agent 0)
        x (ref (with-db file (fetch-keyvals))
               :meta {:file file :watcher watcher})]
    (add-watcher x :send-off watcher persist)
    x))

