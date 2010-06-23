(ns blog.html
  "This namespace holds HTML-rendering code for the main meat of
  our pages (e.g. rendering posts and comments)."
  (:use (hiccup [core :only [html escape-html]]
                [page-helpers :only [link-to]]
                [form-helpers :only [form-to text-field text-area hidden-field]]))
  (:require (clojure.contrib [string :as s])
            (clojure [pprint :as pprint])
            (blog [layout :as layout]
                  [link :as link]
                  [time :as time]
                  [error :as error]
                  [db :as db])))

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

(defn- render-comment
  "Render a comment as HTML, including metadata and avatar etc."
  [comment & {:keys [user even-odd]}]
  [:div {:class (str "comment " even-odd)}
   [:div.gravatar [:img {:src (db/gravatar comment) :alt (:author comment)}]]
   [:div.commentby "Quoth "
    [:span.author (if (comment :homepage)
                    [:a {:href (comment :homepage)} (comment :author)]
                    (comment :author))]
    " on " (time/datestr :pretty (comment :date_created))
    (when user
      [:span.admin
       (layout/status-span comment)
       (link/edit-link comment)])]
   [:div.comment-body (comment :html)]
   [:div.clear]])

(defn- render-comments
  "Render a group of comments, with a header specifying comment count."
  [post & {:keys [user]}]
  [:div#comments
   [:h3 (pprint/cl-format nil "~d Comment~:p" (count (post :comments)))]
   (map #(render-comment %1 :user user :even-odd %2) (post :comments) (cycle ["even" "odd"]))
   (comment-form post)])

(defn post-body
  "Render a post's contents, splitting on \"<!--more-->\"
  (with optional message)."
  [post & {:keys [front-page?]}]
  (if front-page?
    (let [[before -more- after] (s/partition #"<!--more[^>]*-->" (:html post))
          message (when -more-
                    (str (or (-> (re-seq #"<!--more\s*(.*?)\s*-->" -more-)
                                 first second)
                             "Read more")
                         "... &raquo;"))]
      (list before
            (when after
              [:div.center
               (link-to (link/url post) message)])))
    (post :html)))

(defn- render-post*
  "Render a post as HTML, including title, metadata etc.  When :front-page? is true,
  renders 'comments' links.  When false, doesn't."
  ([post & {:keys [user front-page?]}]
     [:div.post
      [:h3 (link/link post)
       (when user
         [:span.admin
          (layout/status-span post)
          (link/edit-link post)])]
      [:div.meta
       (link/link (:category post)) " \u2014 "
       " by " (:username (:user post)) " on " (time/datestr :pretty (post :date_created))]
      [:div.body
       (post-body post :front-page? true)
       (when-let [parent (:parent post)]
         [:div.parent "This post is related to " (link/link parent)])]
      [:div.feedback
       (when (post :tags)
         [:div.post-tags "Tags: " (interpose ", " (map link/link (post :tags)))])
       (when front-page?
         [:div.post-comments-link
          (link/comments-link post)])
       ]]))

(defn render-index [posts & {:keys [user page-number count]}]
  (layout/render-paginated #(render-post* % :front-page? true :user user)
                           posts
                           count
                           page-number))

(defn render-post [post & {:keys [user]}]
  (list (render-post* post :front-page? false :user user)
        (render-comments post :user user)))

(defn render-tag [tag & {:keys [user page-number count]}]
  [:div
   [:h3.info count " Posts Tagged '"
    (link/link tag) "' "
    (link-to (str "/feed/tag/" (:id tag) "/" (:url tag))
             layout/rss-icon)
    ]
   (render-index (:posts tag)
                 :page-number page-number
                 :count count
                 :user user)])

(defn render-category [cat & {:keys [user page-number count]}]
  [:div
   [:h3.info count " Posts in Category '"
    (link/link cat) "' "
    (link-to (str "/feed/category/" (:id cat) "/" (:url cat))
             layout/rss-icon)]
   (render-index (:posts cat)
                 :count count
                 :page-number page-number
                 :user user)])

(defn render-post-table
  ([posts] (render-post-table posts (count posts)))
  ([posts n]
     (list
      [:tr
       [:th "Date"]
       [:th "Title"]
       [:th "Category"]
       [:th "Comments"]]
      (for [post posts]
        [:tr
         [:td (time/datestr :short (:date_created post))]
         [:td (link/link post)]
         [:td (link/link (:category post))]
         [:td (:num_comments post)]])))
  )
