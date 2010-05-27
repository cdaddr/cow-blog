(ns blog.pages
  (:use (hiccup [core :only [html]]
                [page-helpers :only [link-to]]
                [form-helpers :only [form-to text-field text-area]]))
  (:require (blog [layout :as layout]
                  [link :as link]
                  [db :as db]
                  [time :as time])
            (clojure [pprint :as pprint]))
  (:refer-clojure :exclude [comment]))

(defn- post-comments-link [post]
  (when post
    (link-to (str "/post/" (post :id) "#comments")
             (pprint/cl-format nil "~a Comment~:*~[s~;~:;s~]"
                               (count (post :comments))))))

(defn- render-post
  ([admin post] (render-post admin post false))
  ([admin post front-page?]
     [:div.post
      [:h3.storytitle (link/link post)
       (when admin
         [:span " [" (link-to (str "/admin/edit-post/" (post :id)) "Edit") "]"])]
      [:div.meta
       [:div "Category: " (link/link (:category post))]
       [:div "Posted " (time/datestr :pretty (post :date_created))]
       ]
      [:div.storycontent (post :html)]
      [:div.feedback
       (when (post :tags)
        [:div.post-tags "Tags: " (interpose ", " (map link/link (post :tags)))])
       (when front-page?
         [:div.post-comments-link
          (post-comments-link post)])]]))

(defn- render-comment [admin comment]
  [:div.comment
   [:div.comment-gravatar [:img {:src (:gravatar comment) :alt (:author comment)}]]
   [:div.comment-content
    [:h3 "Quoth " (comment :author) ":"
     (when admin
       [:span " [" (link-to (str "/admin/edit-comment/" (comment :id)) "Edit") "]"])]
    [:div.comment-body (comment :html)]
    [:div.comment-meta (comment :date)]]])

(defn- render-comments [admin post]
  [:div#comments
   [:h3 (pprint/cl-format nil "~d Comment~:p" (count (post :comments)))]
   (map (partial render-comment admin) (post :comments))])

(defn- comment-form [post]
  [:div.comment-form
   [:h2 "Add Comment"]
   [:div#add-comment
    (form-to [:post (str "/add-comment/" (post :id))]
             (layout/form-row text-field "author" "Author")
             (layout/form-row text-field "email" "Email")
             (layout/form-row text-field "url" "URL")
             (layout/form-row text-area "markdown" "Comment")
             (layout/submit-row "Submit"))
    (layout/preview-div)]])

(defn index-page
  ([] (index-page 1))
  ([paginate]
     {:status 200
      :body (html (map #(render-post false % true) (db/posts)))}))

(defn redirect-to-id-page [title]

  )

(defn post-page [title]
  (if-let [post (db/post title)]
    {:title (post :title)
     :body (list
            (render-post false post)
            (render-comments false post)
            (comment-form post))}
    :next))

#_(
 (ns blog.pages
   (:use (clojure.contrib def
                          str-utils
                          pprint)
         (compojure.html gen page-helpers form-helpers)
         (compojure.http session helpers)
         (blog config layout db util)))


 (defn- gravatar [comment]
   (let [md5 (md5sum (or (:email comment) (:ip comment)))]
     (str "http://gravatar.com/avatar/" md5 ".jpg?d=identicon")))


 (defn- comment-form [post]
   [:div.comment-form
    [:h2 "Add Comment"]
    [:div#add-comment
     (form-to [:post (str "/add-comment/" (post :id))]
              (form-row text-field "author" "Author")
              (form-row text-field "email" "Email")
              (form-row text-field "url" "URL")
              (form-row text-area "markdown" "Comment")
              (submit-row "Submit"))
     (preview-div)]])

 (defn- post-list-page
   ([admin title posts] (post-list-page admin title nil posts))
   ([admin title subtitle posts]
      {:title title
       :body [:div
              (when subtitle
                [:h2 subtitle])
              (map (partial render-post admin) posts)]}))

;; Public pages

 (defn do-add-comment [post-id ip author email homepage markdown]
   (if-let [post (get-post post-id)]
     (do (add-comment post {:author (escape-html author)
                            :email (escape-html email)
                            :url (escape-html homepage)
                            :markdown markdown
                            :ip ip})
         [(flash-assoc :message "Comment added.")
          (redirect-to (url post))])
     (die "Can't find post " post-id)))

 (defn combined-js []
   (apply str (mapcat #(slurp (str-join "/" [PUBLIC-DIR "js" (str % ".js")]))
                      ["jquery" "typewatch" "showdown" "editor"])))

 (defn index-page [admin]
   {:title "Index"
    :body [:div
           (or (seq (map #(render-post admin % true) (all-posts)))
               [:h2 "No posts yet.  :("])]})



 (defn tag-page [admin tag]
   (let [tag (get-tag (escape-html tag))
         title (str "All Posts Tagged '" (:title tag) "'")
         header (html "All Posts Tagged '" (link tag) "'")]
     (post-list-page admin title header (all-posts-with-tag tag))))

 (defn category-page [admin category]
   (let [category (get-category (escape-html category))
         title (str "All Posts in Category '" (:title category) "'")
         header (html "All Posts in Category '" (link category) "'")]
     (post-list-page admin title header (all-posts-with-category category))))
)
