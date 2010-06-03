(ns blog.error)

(defmulti error (fn [code & args] code))

(defmethod error 404 [_ title message]
  {:status 404
   :body [:div
          [:h3 (str "404 - " title)]
          [:p message]]})
