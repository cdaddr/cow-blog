(ns net.briancarper.blog.html.admin
  (use compojure
       (net.briancarper.blog [db :db db]
                             [global :as global])
       (net.briancarper util)
       (net.briancarper.blog.html [layout :as layout]
                                  [forms :as forms])))


(defn new-post-page
  "Returns HTML for a page where a new post or page can be added.  Login required."
  []
  (if-logged-in
   (page "New Post"
         (layout/block nil
                [:h2 "New Post"]
                (post-form "/add")))))

(defn edit-post-page
  "Returns HTML for a page where a post or page can be edited or deleted.  Login required."
  [id]
  (if-logged-in
   (let [post (get-post (bigint id))]
     (page "Edit Post"
           (layout/block nil
                  [:h2 "Edit Post"]
                  (post-form (str "/edit/" (:id post)) post)
                  (form-to [POST (str "/delete/" (:id post))]
                    (submit "Delete")))))))



(defn edit-comment-page
  "Returns HTML for a page to edit a comment.  Login required."
  [id]
  (if-logged-in
   (let [comment (get-comment (bigint id))]
     (page "Edit Comment"
           (layout/block nil
                  (edit-comment-form comment)
                  [:div#preview]
                  (form-to [POST (str "/remove-comment/" (:id comment))]
                    (submit "Delete")))))))


(defn moderate-comment-line
  "Returns HTML for information about a single comment, and links for editing that comment.  Login required."
  [comment]
  (if-logged-in
   [:div.moderate-comment
    [:p (:id comment)]
    [:p (format-time (:created comment))]
    [:p
     [:strong (:author comment)] " [" (:email comment)
     "] [" (link-to (:homepage comment)
                    (:homepage comment))
     "] [" (:ip comment) "]"]
    (:html comment)
    [:p (link-to (str "/edit-comment/" (:id comment))
                 "Edit")
     " | "
     (let [post (get-post (:post_id comment))]
       (link-to (:url post) (:title post)))]]))

(defn moderate-comments-page
  "Returns HTML for a list of the last 30 comments, with links to edit them.  Login required."
  []
  (if-logged-in
   (page "Moderate Comments"
         (layout/block nil
                [:h2 "Moderate Comments"]
                (map #(vector :div (edit-comment-form %) [:hr])
                     (all-unapproved-comments)))
         (layout/block nil
                [:h2 "Last 30 Comments"]
                (map moderate-comment-line (take 30 (all-comments)))))))