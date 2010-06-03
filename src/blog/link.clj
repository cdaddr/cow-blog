(ns blog.link
  (:require (blog [util :as util])
            (clojure [pprint :as pprint])))

(defmulti url #(:table (meta %)))

(defmethod url :tags [tag]
  (str "/tag/" (tag :url)))

(defmethod url :categories [cat]
  (str "/category/" (cat :url)))

(defmethod url :comments [comment]
  (url (:post comment)))

(defmethod url :posts [post]
  (str "/post/" (post :url)))

(defmethod url :default [x]
  (util/die "Don't know how to make a url out of a " (:table (meta x))))

(defn link [x]
  [:a 
   {:class (str (:table (meta x)) "-link")
    :href (url x)}
   (x :title)])

(defn comments-link [post]
  [:a {:href (str (url post) "#comments")}
   (pprint/cl-format nil "~a Comment~:*~[s~;~:;s~]"
                     (count (post :comments)))])
