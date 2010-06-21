(ns blog.pages
  (:require (blog [layout :as layout]
                  [link :as link]
                  [db :as db]
                  [time :as time]
                  [config :as config]
                  [util :as util]
                  [flash :as flash]
                  [error :as error]
                  [html :as html])
            (oyako [core :as oyako]
                   [query :as q])
            [ring.util [response :as response]]
            (clojure [pprint :as pprint])
            (clojure.contrib [string :as s])
            (hiccup [core :as hiccup])))

(defn index-page
  "Main index page."
  [& {:keys [user page-number] :or {page-number 1}}]
  (let [posts (oyako/fetch-all db/posts
                               :limit config/POSTS-PER-PAGE
                               :offset (config/page-offset page-number)
                               :admin? user
                               :post-type "blog")
        c (oyako/fetch-one (db/count-rows :posts) :admin? user)]
    (if (seq posts)
      {:body (html/render-index posts
                                :count (:count c)
                                :user user
                                :page-number page-number)}
      (error/error 404 "There's nothing here."
                   "No posts have been written yet.  Blog owner: start writing!"))))

(defn post-page
  "Single-post page."
  [id & {:keys [user]}]
  (let [post (oyako/fetch-one db/posts
                              :id id
                              :admin? user
                              :include (q/query-> db/comments
                                                  :admin? user))]
    (if post
      {:title (:title post)
       :body (html/render-post post :user user)}
      (error/error 404 "Post not found"
                   "Whatever post you were looking for isn't here.  Sorry."))))

(defn tag-page
  "Tag page (posts with some tag)."
  [id & {:keys [user page-number] :or {page-number 1}}]
  (let [posts-query (q/query-> db/posts
                               :admin? user
                               :limit config/POSTS-PER-PAGE
                               :post-type "blog"
                               :offset (config/page-offset page-number))]
   (if-let [tag (oyako/fetch-one db/tags
                                 :id id
                                 :include posts-query)]
     {:title (str "Tag " (:title tag))
      :body (html/render-tag tag
                             :count (:num_posts tag)
                             :user user
                             :page-number page-number)}
     (error/error 404 "No Such Tag"
                  "Whatever tag you're looking for isn't here."))))

(defn category-page
  "Category page (posts in some category)."
  [id & {:keys [user page-number] :or {page-number 1}}]
  (let [posts-query (q/query-> db/posts
                               :admin? user
                               :limit config/POSTS-PER-PAGE
                               :post-type "blog"
                               :offset (config/page-offset page-number))]
   (if-let [cat (oyako/fetch-one db/categories
                                 :id id
                                 :include posts-query)]
     {:title (str "Category " (:title cat))
      :body (html/render-category cat
                                  :count (:num_posts cat)
                                  :user user
                                  :page-number page-number)}
     (error/error 404 "No Such Category"
                  "Whatever category you're looking for isn't here."))))

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
  [ip referer post-id author email homepage markdown captcha]
  (let [spam? (not (re-matches config/CAPTCHA captcha))]
    (if-let [post (oyako/fetch-one :posts :id post-id)]
      (error/redirecting-to
       referer
       (not post)        "Tried to add a comment for a post that doesn't exist."
       (empty? ip)       "Missing IP?  That shouldn't happen."
       (empty? markdown) "You forgot to type words in your comment.  Please type some words."
       (and (not (empty? homepage))
            (try (java.net.URL. homepage) nil
                 (catch java.net.MalformedURLException _ :failed)))
       "Invalid homepage.  Please provide a URL starting with 'http[s]://'.  Or leave it blank."

       :else (try
               (oyako/insert :comments
                             {:post_id (:id post)
                              :status (if spam? "spam" "public")
                              :author (or (hiccup/escape-html author) config/DEFAULT-COMMENT-AUTHOR)
                              :email (hiccup/escape-html email)
                              :homepage (when (not (empty? homepage))
                                          (hiccup/escape-html homepage))
                              :markdown markdown
                              :ip ip})
               (db/update-counts)
               (flash/message (if spam?
                                "Comment added, awaiting moderation.
                                     (Anti-robot defense systems activated.)"
                                "Comment added.  Thanks!"))
               (response/redirect referer)
                   
               (catch Exception e
                 (throw e)
                 (error/redirect-and-error referer
                                           "There was some kind of database error and
                                          the computer ate your comment.  Sorry.  :(")))))))



