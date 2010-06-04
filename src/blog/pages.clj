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
  ([post & {:keys [user front-page?]}]
     [:div.post
      [:h3.storytitle (link/link post)
       (when user
         [:span.admin
          (layout/status-span post)
          (link/edit-link post)])]
      [:div.meta
       [:div "Category: " (link/link (:category post))]
       [:div "Posted by " (:username (:user post)) " on " (time/datestr :pretty (post :date_created))]]
      [:div.storycontent (post :html)]
      [:div.feedback
       (when (post :tags)
        [:div.post-tags "Tags: " (interpose ", " (map link/link (post :tags)))])
       (when front-page?
         [:div.post-comments-link
          (link/comments-link post)])
       ]]))

(defn- render-comment
  "Render a comment as HTML, including metadata and avatar etc."
  [comment & {:keys [user]}]
  [:div
   [:div.gravatar [:img {:src (db/gravatar comment) :alt (:author comment)}]]
   [:div.comment
    [:div.commentby "Quoth "
     [:span.author (if (comment :homepage)
                     [:a {:href (comment :homepage)} (comment :author)]
                     (comment :author))]
     " on " (time/datestr :pretty (comment :date_created))
     (when user
       [:span.admin
        (layout/status-span comment)
        (link/edit-link comment)])]
    [:div.comment-body (comment :html)]]
   [:div.clear]])

(defn- render-comments
  "Render a group of comments, with a header specifying comment count."
  [post & {:keys [user]}]
  [:div#comments
   [:h3 (pprint/cl-format nil "~d Comment~:p" (count (post :comments)))]
   (map #(render-comment % :user user) (post :comments))])

(defn- comment-form
  "Render an HTML form suitable for creating a new comment, plus preview div."
  [post]
  [:div.comment-form
   [:h3 "Speak your Mind"]
   [:div#add-comment
    (form-to [:post (str "/comment")]
             (hidden-field "post-id" (:id post))
             (layout/form-row "Author" "author" text-field)
             (layout/form-row "Email" "email" text-field)
             (layout/form-row "URL" "homepage" text-field)
             (layout/form-row "Comment" "markdown" text-area)
             [:div.test
              (text-field "test" "Type this word =>")
              [:img {:src "/img/test.jpg"}]]
             (layout/submit-row "Submit"))
    [:div.feedback "You can use " [:a {:href "http://daringfireball.net/projects/markdown/"} "Markdown"] " in your comment."]
    [:div.feedback "Email/URL are optional.  Email is only used for " (link-to "http://www.gravatar.com/" "Gravatar") "."]
    (layout/preview-div)]])

;; PAGES

(defn index-page
  "Main index page."
  [& {:keys [user page-number]}]
  (let [posts (db/posts :include-hidden? user)]
    (if (empty? posts)
      {:body [:div [:h3 "There's nothing here."]
              [:p "No posts have been written yet.  Start writing!"]]}
      {:body [:div
              (layout/render-paginated #(render-post % :front-page? true :user user)
                                       page-number posts)]}
      )))

(defn post-page
  "Page to display a single post, given the title of the post."
  [title & {:keys [user]}]
  (if-let [post (db/post title :include-hidden? user)]
    {:title (post :title)
     :body (list
            (render-post post :front-page? false :user user)
            (render-comments post :user user)
            (comment-form post))}
    (error/error 404 "No such post"
                 (str "There's no post named '" (escape-html title) "'."))))

(defn- post-list-page
  "Page to render a list of posts."
  ([title subtitle posts page-number & {:keys [user]}]
     {:title title
      :body [:div
             [:h3.info subtitle]
             (if (empty? posts)
               "No posts found."
               (layout/render-paginated #(render-post % :front-page? true)
                                        page-number posts))]}))

(defn tag-page
  "Page to render all posts with some tag url."
  [tag-name & {:keys [user page-number]}]
  (if-let [tag (db/tag (escape-html tag-name) :include-hidden? user)]
    (let [title (str "All Posts Tagged '" (:title tag) "'")
          header [:div (count (:posts tag)) " Posts Tagged '" (link/link tag) "'"]]
      (post-list-page title header (:posts tag) page-number))
    (error/error 404 "Invalid Tag"
                 (str "There's no tag named '" (escape-html tag-name) "'."))))

(defn category-page
  "Page to render all posts in category with some category name."
  [category-name & {:keys [user page-number] :or {page-number 1}}]
  (if-let [category (db/category (escape-html category-name) :include-hidden? user)]
   (let [title (str "All Posts in Category '" (:title category) "'")
         header [:div (count (:posts category)) " Posts in Category '" (link/link category) "'"]]
     (post-list-page title header (:posts category) page-number))
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
  [post-id ip author email homepage markdown uri captcha]
  (let [post-id (util/safe-int post-id)
        spam? (not (re-matches config/CAPTCHA captcha))]
    (if-let [post (db/bare :posts post-id)]
      (or (error/redirecting-to
           uri
           (not post)        "Tried to add a comment for a post that doesn't exist."
           (empty? ip)       "Missing IP?  That shouldn't happen."
           (empty? markdown) "You forgot to type words in your comment.  Please type some words."
           (and (not (empty? homepage))
                (try (java.net.URL. homepage) nil
                     (catch java.net.MalformedURLException _ :failed)))
           "Invalid homepage.  Please provide a URL starting with 'http[s]://'.  Or leave it blank.")
          (try
            (db/insert
             (db/in-table :comments
                          {:post_id post-id
                           :status_id (if spam?
                                        (:id (db/status "Spam"))
                                        (:id (db/status "Public")))
                           :author (or (escape-html author) config/DEFAULT-COMMENT-AUTHOR)
                           :email (escape-html email)
                           :homepage (when (not (empty? homepage))
                                       (escape-html homepage))
                           :markdown markdown
                           :ip ip}))
            (merge (response/redirect uri)
                   (flash/message (if spam?
                                    "Comment added, awaiting moderation.
                                     (Anti-robot defense systems activated.)"
                                    "Comment added.  Thanks!")))
            (catch Exception e
              (error/redirect-and-error uri "There was some kind of database error and
                                          the computer ate your comment.  Sorry.  :(")))))))


