(ns blog.time
  (:require (blog [util :as util]
                  [config :as config])))

(defn- tz [x]
  (org.joda.time.DateTimeZone/forID x))

(def UTC (tz "UTC"))
(def TIME-ZONE (tz config/TIME-ZONE))

(defmulti to-joda (fn [x] (class x)))
(defmethod to-joda org.joda.time.DateTime [d] d)
(defmethod to-joda java.sql.Timestamp [d]
  (org.joda.time.DateTime. (.getTime d) TIME-ZONE))

(defmethod to-joda :default [x]
  nil)

(defn- fmt [x]
  (.withZone (org.joda.time.format.DateTimeFormat/forPattern x)
             TIME-ZONE))

(defn now []
  (org.joda.time.DateTime.))

(defn expire-date []
  (.plusDays (now) 7))

(def DATE-FORMATS
     {:short   (fmt "yyyy-MM-dd")
      :monthyear (fmt "yyyy-MM")
      :pretty (fmt config/TIME-FORMAT)
      :edit   (fmt "yyyy-MM-dd HH:mm:ss Z")
      :http   (fmt "EEE', 'dd' 'MMM' 'yyyy' 'HH:mm:ss' 'Z")})

(defn str-to-dbdate [format s]
  (if-let [f (DATE-FORMATS format)]
   (java.sql.Timestamp.
    (.getMillis
     (.parseDateTime f s)))
   (util/die "Invalid date format " format)))

(defn datestr [format d]
  (if-let [f (DATE-FORMATS format)]
    (.print f (to-joda d))
    (util/die "Invalid date format " format)))

