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
                  [flash :as flash]
                  [error :as error])
            [ring.util [response :as response]]
            (clojure [pprint :as pprint])
            (clojure.contrib [string :as s])))

(defn- render-post
  "Render a post as HTML, including title, metadata etc.  When :front-page? is true,
  renders 'comments' links.  When false, doesn't."
  ([post & {:keys [front-page?]}]
     [:div.post
      [:h3.storytitle (link/link post)]
      [:div.meta
       [:div "Category: " (link/link (:category post))]
       [:div "Posted by " (:username (:user post)) " on " (time/datestr :pretty (post :date_created))]]
      [:div.storycontent (post :html)]
      [:div.feedback
       (when (post :tags)
        [:div.post-tags "Tags: " (interpose ", " (map link/link (post :tags)))])
       (when front-page?
         [:div.post-comments-link
          (link/comments-link post)])]]))

(defn- render-comment
  "Render a comment as HTML, including metadata and avatar etc."
  [admin comment]
  [:div
   [:div.gravatar [:img {:src (db/gravatar comment) :alt (:author comment)}]]
   [:div.comment
    [:div.commentby "Quoth "
     [:span.author (if (comment :homepage)
                     [:a {:href (comment :homepage)} (comment :author)]
                     (comment :author))]
     " on " (time/datestr :pretty (comment :date_created))
     (when admin
       [:span " [" (link-to (str "/admin/edit-comment/" (comment :id)) "Edit") "]"])]
    [:div.comment-body (comment :html)]]
   [:div.clear]])

(defn- render-comments
  "Render a group of comments, with a header specifying comment count."
  [admin post]
  [:div#comments
   [:h3 (pprint/cl-format nil "~d Comment~:p" (count (post :comments)))]
   (map (partial render-comment admin) (post :comments))])

(defn- comment-form
  "Render an HTML form suitable for creating a new comment, plus preview div."
  [post]
  [:div.comment-form
   [:h3 "Add Comment"]
   [:div#add-comment
    (form-to [:post (str "/comment")]
             (hidden-field "post-id" (:id post))
             (layout/form-row "Author" "author" text-field)
             (layout/form-row "Email" "email" text-field)
             (layout/form-row "URL" "homepage" text-field)
             (layout/form-row "Comment" "markdown" text-area)
             (layout/submit-row "Submit"))
    [:div.feedback "You can use " [:a {:href "http://daringfireball.net/projects/markdown/"} "Markdown"] " in your comment."]
    (layout/preview-div)]])

;; PAGES

(defn index-page
  "Main index page."
  ([] (index-page 1))
  ([& {:keys [page-number]}]
     (let [posts (db/posts)]
       (if-not (empty? posts)
         {:body [:div
                 (layout/render-paginated #(render-post % :front-page? true)
                                          page-number posts)]}
         (error/error 404 "There's nothing here!"
                      "There are no posts that meet your search criteria.  :(")))))

(defn post-page
  "Page to display a single post, given the title of the post."
  [title]
  (if-let [post (db/post title)]
    {:title (post :title)
     :body (list
            (render-post post :front-page? false)
            (render-comments false post)
            (comment-form post))}
    (error/error 404 "No such post"
                 (str "There's no post named '" (escape-html title) "'."))))

(defn- post-list-page
  "Page to render a list of posts."
  ([title subtitle posts page-number]
     {:title title
      :body [:div
             [:h3.info subtitle]
             (if (empty? posts)
               "No posts found."
               (layout/render-paginated render-post page-number posts))]}))

(defn tag-page
  "Page to render all posts with some tag name."
  [tag-name & {:keys [page-number]}]
  (if-let [tag (db/tag (escape-html tag-name))]
    (let [title (str "All Posts Tagged '" (:title tag) "'")
          header (html "All Posts Tagged '" (link/link tag) "'")]
      (post-list-page title header (db/posts-with-tag (:url tag)) page-number))
    (error/error 404 "Invalid Tag"
                 (str "There's no tag named '" (escape-html tag-name) "'."))))

(defn category-page
  "Page to render all posts in category with some category name."
  [category-name & {:keys [page-number] :or {page-number 1}}]
  (if-let [category (db/category (escape-html category-name))]
   (let [title (str "All Posts in Category '" (:title category) "'")
         header (html "All Posts in Category '" (link/link category) "'")]
     (post-list-page title header (db/posts-with-category (:url category)) page-number))
   (error/error 404 "Invalid Category"
                (str "There's no category named '" (escape-html category-name) "'."))))

(defn combined-js
  "Render Javascript files by reading them from disk and concat'ing them
  together into one blob of text.  This saves the user from needed one HTTP
  request per JS file."
  []
  {:headers {"Content-Type" "text/javascript;charset=UTF-8"}
   :body (apply str (mapcat #(slurp (s/join "/" [config/PUBLIC-DIR "js" (str % ".js")]))
                            ["jquery" "typewatch" "showdown" "editor"]))})

(defn do-add-comment
  "Handles POST request to add a new comment.  This is suitable for public /
   anonymous users to use."
  [post-id ip author email homepage markdown uri]
  (let [post-id (util/safe-int post-id)]
    (if-let [post (db/bare :posts post-id)]
      (cond
       (not post)        (error/redirect-and-error uri "Tried to add a comment for a post that doesn't exist.")
       (empty? ip)       (error/redirect-and-error uri "Missing IP?  That shouldn't happen.")
       (empty? markdown) (error/redirect-and-error uri "You forgot to type words in your comment.  Please type some words.")
       (and (not (empty? homepage))
            (try (java.net.URL. homepage) nil
                 (catch java.net.MalformedURLException _ :failed)))
       (error/redirect-and-error uri "Invalid homepage.  Please provide a URL starting with 'http[s]://'.  Or leave it blank.")
       :else (try
               (db/insert
                (db/in-table :comments
                             {:post_id post-id
                              :status_id 1
                              :author (or (escape-html author) config/DEFAULT-COMMENT-AUTHOR)
                              :email (escape-html email)
                              :homepage (when (not (empty? homepage))
                                          (escape-html homepage))
                              :markdown markdown
                              :ip ip}))
               (merge (response/redirect uri)
                      (flash/message "Comment added."))
               (catch Exception e
                 (error/redirect-and-error uri "There was some kind of database error and
                                          the computer ate your comment.  Sorry.  :(")))))))


