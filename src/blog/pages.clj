(ns blog.pages
  (:use (clojure.contrib def
                         str-utils
                         pprint)
        (compojure.html gen page-helpers form-helpers)
        (compojure.http session helpers)
        (blog config layout db util)))

(defn- render-post
  ([admin post] (render-post admin post false))
  ([admin post front-page?]
     [:div.post
      [:h2 (link post)
       (when admin
         [:span " [" (link-to (str "/admin/edit-post/" (post :id)) "Edit") "]"])]
      [:div.post-category
       "Posted into category " (link (:category post))]
      [:div.post-body (post :html)]
      [:div.post-tags "Tags: " (interpose ", "  (map link (post :tags)))]
      [:div.post-meta "Posted " (pretty-date (post :date))]
      (when front-page?
        [:div.post-comments-link
         (post-comments-link post)])
      ]))

(defn- gravatar [comment]
  (let [md5 (md5sum (or (:email comment) (:ip comment)))]
    (str "http://gravatar.com/avatar/" md5 ".jpg?d=identicon")))

(defn- render-comment [admin comment]
  [:div.comment
   [:div.comment-gravatar (img {:alt (:author comment)}
                               (gravatar comment))]
   [:div.comment-content
    [:h3 "Quoth " (comment :author) ":"
     (when admin
       [:span " [" (link-to (str "/admin/edit-comment/" (comment :id)) "Edit") "]"])]
    [:div.comment-body (comment :html)]
    [:div.comment-meta (comment :date)]]])

(defn- render-comments [admin post]
  [:div#comments
   [:h2 (cl-format nil "~d Comment~:p" (count (get-comments post)))]
   (map (partial render-comment admin) (get-comments post))])

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

(defn post-page [admin post-id]
  (if-let [post (get-post post-id)]
    {:title (post :title)
     :body (list
            (render-post admin post)
            (render-comments admin post)
            (comment-form post))}
    :next))

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
