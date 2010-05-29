(ns blog.pages
  (:use (hiccup [core :only [html escape-html]]
                [page-helpers :only [link-to]]
                [form-helpers :only [form-to text-field text-area hidden-field]]))
  (:require (blog [layout :as layout]
                  [link :as link]
                  [db :as db]
                  [time :as time]
                  [config :as config]
                  [util :as util]
                  [gravatar :as gravatar]
                  [flash :as flash])
            [ring.util [response :as response]]
            (clojure [pprint :as pprint])
            (clojure.contrib [string :as s])))

(defn- render-post
  ([admin post] (render-post admin post false))
  ([admin post front-page?]
     [:div.post
      [:h3.storytitle (link/link post)
       (when admin
         [:span " [" (link-to (str "/admin/edit-post/" (post :id)) "Edit") "]"])]
      [:div.meta
       [:div "Category: " (link/link (:category post))]
       [:div "Posted " (time/datestr :pretty (post :date_created))]]
      [:div.storycontent (post :html)]
      [:div.feedback
       (when (post :tags)
        [:div.post-tags "Tags: " (interpose ", " (map link/link (post :tags)))])
       (when front-page?
         [:div.post-comments-link
          (link/comments-link post)])]]))

(defn- render-comment [admin comment]
  [:div
   [:div.gravatar [:img {:src (gravatar/gravatar (or (:email comment) (:ip comment))) :alt (:author comment)}]]
   [:div.comment
    [:div.commentby "Quoth " [:span.author (comment :author)] " on " (time/datestr :pretty (comment :date_created))
     (when admin
       [:span " [" (link-to (str "/admin/edit-comment/" (comment :id)) "Edit") "]"])]
    [:div.comment-body (comment :html)]]
   [:div.clear]])

(defn- render-comments [admin post]
  [:div#comments
   [:h3 (pprint/cl-format nil "~d Comment~:p" (count (post :comments)))]
   (map (partial render-comment admin) (post :comments))])

(defn- comment-form [post]
  [:div.comment-form
   [:h3 "Add Comment"]
   [:div#add-comment
    (form-to [:post (str "/comment")]
             (hidden-field "post-id" (:id post))
             (layout/form-row text-field "author" "Author")
             (layout/form-row text-field "email" "Email")
             (layout/form-row text-field "homepage" "URL")
             (layout/form-row text-area "markdown" "Comment")
             [:div.feedback "You can use " [:a {:href "http://daringfireball.net/projects/markdown/"} "Markdown"] " in your comment."]
             (layout/submit-row "Submit"))
    (layout/preview-div)]])

(defn index-page
  ([] (index-page 1))
  ([paginate]
     {:status 200
      :body (html (map #(render-post false % true) (db/posts)))}))

(defn post-page [title]
  (if-let [post (db/post title)]
    {:title (post :title)
     :body (list
            (render-post false post)
            (render-comments false post)
            (comment-form post))}
    :next))

(defn- post-list-page
  ([admin title posts] (post-list-page admin title nil posts))
  ([admin title subtitle posts]
     {:title title
      :body [:div
             (when subtitle
               [:h3.info subtitle])
             (map (partial render-post admin) posts)]}))

(defn tag-page [tag]
  (let [tag (db/tag (escape-html tag))
        title (str "All Posts Tagged '" (:title tag) "'")
        header (html "All Posts Tagged '" (link/link tag) "'")]
    (post-list-page false title header (db/posts-with-tag (:url tag)))))

(defn category-page [category]
  (let [category (db/category (escape-html category))
        title (str "All Posts in Category '" (:title category) "'")
        header (html "All Posts in Category '" (link/link category) "'")]
    (post-list-page false title header (db/posts-with-category (:url category)))))

(defn combined-js []
  {:headers {"Content-Type" "text/javascript;charset=UTF-8"}
   :body (apply str (mapcat #(slurp (s/join "/" [config/PUBLIC-DIR "js" (str % ".js")]))
                            ["jquery" "typewatch" "showdown" "editor"]))})

(defn- redirect-and-error [uri txt]
  (merge (response/redirect (or uri "/"))
         (flash/error txt)))

(defn do-add-comment [post-id ip author email homepage markdown uri]
  (let [post-id (db/safe-int post-id)]
    (if-let [post (db/bare :posts post-id)]
      (cond
       (not post)        (redirect-and-error uri "Tried to add a comment for a post that doesn't exist.")
       (empty? ip)       (redirect-and-error uri "Missing IP?  That shouldn't happen.")
       (empty? markdown) (redirect-and-error uri "You forgot to type words in your comment.  Please type some words.")
       :else (try
               (db/insert
                (db/in-table :comments
                             {:post_id post-id
                              :status_id 1
                              :author (or (escape-html author) config/DEFAULT-COMMENT-AUTHOR)
                              :email (escape-html email)
                              :homepage (escape-html homepage)
                              :markdown markdown
                              :ip ip}))
               (merge (response/redirect uri)
                      (flash/message "Comment added."))
               (catch Exception e
                 (redirect-and-error uri "There was some kind of database error and
                                          the computer ate your comment.  Sorry.  :(")))))))


