(ns blog.flash)

(defn message [x]
  {:flash {:message x}})

(defn error [x]
  {:flash {:error x}})
