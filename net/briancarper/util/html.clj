(ns net.briancarper.util.html)

(defn li [& rest]
  `[:li ~@rest])

(defn image [url & args]
  (let [args (apply hash-map args)]
    [:img (merge {:src url :alt url}
                 args)]))

(defn text-area [name value]
  [:textarea {:id name
              :name name
              :class "resizable markdown"
              :rows 15
              :cols 70} value])

