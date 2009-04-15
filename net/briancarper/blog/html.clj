(ns net.briancarper.blog.html
  (:use (net.briancarper.blog global db config)
        (net.briancarper.blog.html [layout :as layout]
                                   [forms :as forms]
                                   [error-pages :as error-pages]
                                   [feed :as feed])
        (net.briancarper util)
        compojure
        (compojure.http request)
        (clojure.contrib str-utils seq-utils java-utils))
  (:import (java.util Calendar)))


(defn image [url & args]
  (let [args (apply hash-map args)]
    [:img (merge {:src url :alt url}
                 args)]))

;; Combine CSS and JS files to avoid hammering the server with requests for them

(defn combined-css []
  (let [date (expire-date)]
    [{"Cache-Control" "max-age=3600;must-revalidate"
      "Expires" (http-date date)}
     (mapcat #(slurp (str "static-blog/css/" % ".css"))
               ["reset-fonts-grids" "style" "zenburn"])]))

(defn combined-js []
  (let [date (expire-date)]
    [{"Cache-Control" "max-age=3600;must-revalidate"
      "Expires" (http-date date)}]
    (apply str (concat (map #(slurp (str "static-blog/js/" % ".js"))
                            ["jquery" "typewatch" "jquery.textarearesizer.compressed" "highlight" "languages/lisp" "languages/ruby" "showdown" "editor"])))))

;; Post block - the guts of all pages that show a blog post or static page

(declare comments-link)
(defn post-block
  "Given a post, returns HTML for the contents of that post (its title, post text, meta-data (date, category, tags etc.).  opts is a series of key/value pairs.  Currently one option is accepted, :single-page; if true, suppresses display of the 'comments' link."
  [post & opts]
  (let [opts (apply hash-map opts)]
    (block post
           [:h2
            (link-to (:url post) (:title post))]
           (if-let [parent (:parent post)]
              [:div.parent
               [:h3 "* This page is a child of \"" (link-to (:url parent) (:title parent)) "\"."]])
           [:div.content
            (:html post)]
           [:div.post-footer
            [:div.meta
             [:div.meta-row
              (if (= "blog" (:type post))
                [:span "Posted into " (link-to (:url (:category post))
                                               (:name (:category post)))]
                "Posted")
              " on " (format-date (:created post))]
             (when (:edited post)
               [:div.meta-row
                (str "Last updated " (format-date (:edited post)))])
             [:div.meta-row.comments
              (if-logged-in
               [:span
                (link-to (str "/edit/" (:id post)) "Edit")
                " | "])
              (if (not (:single-page opts))
                (comments-link post))]
             [:div.meta-row
              "Tags: " (map #(vector :a.tag {:href (:url %)} (:name %)) (:tags post))]]])))

;; Login / Logout

(defn login-page []
  (page "Login"
        (block nil
         (form-to [POST "/login"]
           (field text-field "name" "Username")
           (form-row
            (label "password" "Password")
            (password-field "password"))
           (submit "Login")))))

;; Comments helpers

(defn comments-link
  "Returns HTML for a link to the comments anchor on the post page for some post."
  [post]
  (let [c (count (:comments post))]
    [:span.comment-link
     (link-to (str (:url post) "#comments")
              (str c
                   (if (= c 1)
                     " Comment"
                     " Comments")))]))

(defn comment-line
  "Returns HTML for a user comment (one item in a displayed list of comments)."
  [comment even-odd]
  [:div {:class (str "comment-line " even-odd)}
   [:div.comment-avatar
    (image (:avatar comment))]
   [:div.comment-content
    [:span.author (if (not (empty? (:homepage comment)))
                    (link-to (:homepage comment) (:author comment))
                    (:author comment))]
    " says:"
    [:div
     (:html comment)]]
   [:div.comment-date (format-time (:created comment))
    (if-logged-in
     [:span " | " (link-to (str "/edit-comment/" (:id comment))
                           "Edit")])]
   [:div.clear]])

(defn comment-list
  "Returns HTML for a list of user comments for a post."
  [post]
  (map comment-line (:comments post) (interleave (repeat "even")
                                                 (repeat "odd"))))

(defn comment-block
  "Returns HTML for the comments section of a single post page, including the list of user comments for this post and a place for users to add new comments."
  [post]
  [:div#comments
   (when (> (count (:comments post)) 0)
     (block nil
            [:div
             [:h2 (count (:comments post)) " Comments"]
             (comment-list post)]))
   (block nil
          [:h2 "Speak Your Mind"]
          [:div.form.clear (comment-form post)]
          [:h2 "Preview"]
          [:div#preview]
          [:p]
          [:h2 "Commenting Help"]
          [:div#comment-instructions
           [:h3 "Email / Avatar"]
           [:ul
            [:li "Supply your email address and your " (link-to "http://en.gravatar.com/" "Gravatar") " will be used."]
            [:li "I will never email you and your address won't be published."]]
           [:h3.showhide "No HTML allowed! " (comment [:a#showhide {:href  ""} "Show help?"])]
           [:div
            [:p "All HTML is auto-escaped.  Use " (link-to "http://daringfireball.net/projects/markdown/basics" "Markdown") ".  Examples:" ]
            [:ul
             [:li "*emphasis* = " [:em "emphasis"]]
             [:li "**strong** = " [:strong "strong"]]
             [:li "[link](http://foo.bar) = &lt;a href=\"http://foo.bar\">link&lt;/a>"]
             [:li "`code in backticks` = " [:code "code in backticks"]]
             [:li "&nbsp;&nbsp;&nbsp;&nbsp;code indented 4 spaces = " [:pre [:code "code indented four spaces"]]]
             [:li "> Angle-brace quoted text = " [:blockquote "Angle-brace quoted text"]]]]]
)])

;; Pages

(defn index-page
  "Returns HTML for the main index page."
  []
  (if-let [all (all-blog-posts)]
    (let [posts (paginate all)]
      (page nil
            (map #(post-block %) posts)
            (pagenav all)))
    (error "No posts found.")))

(defn category-page
  "Returns HTML for a page that lists all the posts in some category."
  [cat]
  (let [cat (get-category cat)
        cat-posts (all-posts-with-category cat)
        posts (paginate cat-posts)]
    (if posts
      (page (str "Category " (:name cat))
            (block nil
                   [:h2 (count cat-posts) " Posts in Category \"" (link-to (:url cat) (:name cat)) "\""]
                   [:h4
                    (image "/img/rss.png" :class "rss")
                    (link-to (str "/feed/category/" (:permalink cat))
                             "RSS Feed for \"" (:name cat) "\" Category")])
            (map post-block posts)
            (pagenav cat-posts))
      (error "No posts in this category."))))

(defn static-file
  "If a file exists, serves the file.  Otherwise 404 error."
  [filename]
  (let [f (file (str "static-blog/" filename))
        date (expire-date)]
    (if (and (.exists f)
             (.isFile f))
      (if (re-find #"\.txt$" filename)
        [{:headers {"Content-Type" "text/plain"}}
         f]
        [{:headers {"Cache-Control" "max-age=3600;must-revalidate"
           "Expires" (http-date date)}}
         f])
      (error-404))))

(defn blog-page
  "Returns HTML for a page that displays the contents of a single blog post including its comments."
  ([page]
     (blog-page nil page))
  ([parent child]
     (let [parent (get-post parent)
           child (get-post child)]
       (if child
         (page (:title child)
               (post-block child :single-page true)
               (comment-block child))
         (error-404)))))

(defn static-page
  "Returns HTML for a page that displays the contents of a single page including its comments."
  [path]
  (let [permalinks (re-split #"/" path)
        [curr-page & pages] (reverse (map get-post permalinks))]
    (if curr-page
      (page (:title curr-page)
            (post-block curr-page :single-page true)
            (comment-block curr-page))
      (error-404))))

(defn tag-page
  "Returns HTML for a page that lists all posts with a certain tag."
  [name]
  (let [tag (get-tag name)
        tag-posts (all-posts-with-tag tag)
        posts (paginate tag-posts)]
    (if posts
      (page (str "Tag: " (:name tag))
            (block nil
                   [:h2 (str (count tag-posts) " Posts Tagged \"" (html (link-to (:url tag) (:name tag))) "\"")]
                   [:h4 (image "/img/rss.png" :class "rss")
                    (link-to (str "/feed/tag/" (:permalink tag)) "RSS Feed for \"" (:name tag) "\" Tag")])
            (map post-block posts)
            (pagenav tag-posts))
      (error "There are no posts with this tag."))))


;; Archives

(defn tag-cloud
  "Returns HTML for a tag cloud (logarithmically scaled)."
  []
  (block nil
         [:h2 "Tags"]
         (let [tags-with-counts (all-tags-with-counts)]
           (when (not (empty? tags-with-counts))
             (let [sorted-tags-counts (sort-by #(.toLowerCase (:name (first %)))
                                               tags-with-counts)
                   counts (map second sorted-tags-counts)
                   max-count (apply max counts)
                   min-count (apply min counts)
                   min-size 90.0
                   max-size 200.0
                   color-fn (fn [val]
                              (let [b (min (- 255 (Math/round (* val 255)))
                                           200)]
                                (str "rgb(" b "," b "," b ")")))
                   tag-fn (fn [[tag c]]
                            (let [weight (/ (- (Math/log c) (Math/log min-count))
                                            (- (Math/log max-count) (Math/log min-count)))
                                  size (+ min-size (Math/round (* weight
                                                                  (- max-size min-size))))
                                  color (color-fn (* weight 1.0))]
                              [:a {:href (str "/tag/" (:permalink tag))
                                   :style (str "font-size: " size "%;"
                                               "color:" color)}
                               (:name tag)]))]
               [:div.tag-cloud
                (apply html (interleave (map tag-fn sorted-tags-counts)
                                        (repeat " ")))])))))
(defn post-table
  "Returns HTML for a table that displays links to posts, suitable for an archives page."
  [posts]
  [:table#post-table.post-table.sortable
          [:thead
           [:tr
            (map #(vector :th %)
                 ["Date" "Title" "Category" "Comments"])
            (if-logged-in
             [:th.unsortable "Edit"])]]
          (map (fn [post]
                 [:tr
                  [:td.nowrap (format-date (:created post))]
                  [:td (link-to (:url post) (:title post))]
                  [:td (if (= "blog" (:type post))
                         (link-to (:url (:category post)) (:name (:category post)))
                         "Static page")]
                  [:td.nowrap (comments-link post)]
                  (if-logged-in
                   [:td (link-to (str "/edit/" (:id post)) "Edit")])
                  ])
               posts)])

(defn archives-all
  "Returns HTML for a block containing a table list of all posts and pages."
  []
  (block nil
         [:h2 "Everything"]
         (post-table (all-posts))))

(defn archives-most-discussed
  "Returns HTML for a block containing a table list of the top 15 most discussed posts and pages."
  []
  (block nil
         [:h2 "Most Discussed"]
         (post-table
          (take 15
                (reverse (sort-by :comments-count (all-posts)))))))

(defn archives-page
  "Returns HTML for a page that displays archives (tag cloud and table lists of posts and pages)."
  []
  (page "Archives"
        (tag-cloud)
        (archives-most-discussed)
        (archives-all)))

;; Search results

(defn search-results
  "Returns HTML for a page displaying search results for some query."
  []
  (let [terms (*param* :q)
        all-results (search-posts terms)
        results (paginate all-results)]
    (page "Search Results"
          (block nil
                 [:h2 (str (count all-results) " Results for Search: \"") terms "\""])
          (map post-block results)
          (pagenav all-results (fn [x] (str "?q=" terms "&" x))))))
