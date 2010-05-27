(ns blog.time
  (:require (blog [util :as util]
                  [config :as config])))

(defn- tz [x]
  (org.joda.time.DateTimeZone/forID x))

(def UTC (tz "UTC"))
(def TIME-ZONE (tz config/TIME-ZONE))

(defn- fmt [x]
  (.withZone (org.joda.time.format.DateTimeFormat/forPattern x)
             TIME-ZONE))

(def DATE-FORMATS
     {:pretty (fmt config/TIME-FORMAT)
      :edit   (fmt "yyyy-MM-dd HH:mm:ss Z")})

(defn str-to-dbdate [format s]
  (if-let [f (DATE-FORMATS format)]
   (java.sql.Timestamp.
    (.getMillis
     (.parseDateTime f s)))
   (util/die "Invalid date format " format)))

(defn datestr [format d]
  (if-let [f (DATE-FORMATS format)]
    (.print f (org.joda.time.DateTime. (.getTime d) TIME-ZONE))
    (util/die "Invalid date format " format)))

