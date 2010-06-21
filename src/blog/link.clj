(ns blog.link
  (:require (blog [util :as util])
            (clojure [pprint :as pprint])))

(defmulti url #(:table (meta %)))

(defmethod url :tags [tag]
  (str "/tag/" (tag :id) "/" (tag :url)))

(defmethod url :categories [cat]
  (str "/category/" (cat :id) "/" (cat :url)))

(defmethod url :comments [comment]
  (url (:post comment)))

(defmethod url :posts [post]
  (str "/"
   (condp = (:title (:type post))
       "Blog" "blog"
       "Page" "page"
       "Toplevel Page" "page"
       "blog")
   "/" (:id post) "/" (:url post)))

(defmethod url :default [x]
  "ERROR"
  #_(util/die "Don't know how to make a url out of a " (:table (meta x))))

(defmulti edit-url (fn [x] (:table (meta x))))
(defmethod edit-url :posts [x]
  (str "/admin/edit-post/" (:id x)))
(defmethod edit-url :comments [x]
  (str "/admin/edit-comment/" (:id x)))

(defn edit-link [x]
  [:span.edit " [" [:a {:href (edit-url x)} "edit"] "]"])

(defn link [x]
  [:a 
   {:class (str (:table (meta x)) "-link")
    :href (url x)}
   (x :title)])

(defn comments-link [post]
  [:a {:href (str (url post) "#comments")}
   (pprint/cl-format nil "~a Comment~:*~[s~;~:;s~]" (:num_comments post))])
