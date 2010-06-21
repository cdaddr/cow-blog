(ns blog.error
  (:require (blog [flash :as flash])
            (ring.util [response :as response])))

(defn error [code title message]
  {:status code
   :body [:div
          [:h3 (str code " - " title)]
          [:p message]]})

(defn redirect-and-error
  "Return a response map suitable to redirect the user to some URI
  with a given error message text in the flash."
  [uri txt]
  (merge (response/redirect (or uri "/"))
         (flash/error txt)))

(defmacro redirecting-to [uri & conditions]
  (let [xs (partition 2 conditions)
        [conditions else] [(butlast xs) (last xs)]
        _uri (gensym "uri")]
    `(let [~_uri ~uri]
       (cond ~@(mapcat (fn [[condition text]]
                         (list condition `(redirect-and-error ~_uri (str "FAIL: " ~text))))
                       conditions)
             ~@else))))

(defmacro with-err-str [& body]
  `(let [s# (java.io.StringWriter.)
         p# (java.io.PrintWriter. s#)]
     (binding [*err* p#]
       (try ~@body (catch Exception e#
                     (throw (Exception. (str s# e#))))))))
