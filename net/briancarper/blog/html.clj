(ns net.briancarper.blog.html
  (:use (net.briancarper.blog db config)
        (net.briancarper util)
        (net.briancarper.util html)
        compojure
        (clojure.contrib str-utils seq-utils java-utils))
  (:import (java.util Calendar)))

(declare navbar error submit)

;; server.clj will bind these (thread-locally) to give us global access to
;; various things we'd otherwise have to constantly pass around between functions.
(def *session* nil)
(def *param* nil)
(def *request* nil)

(defn- die [something]
  (throw (Exception. (str "--->" something "<---"))))

(defn message [s])
(defn error-message [s])

;; These functions implement a rudimentary Rails-like "flash" system
;; to add a message into a session and delete it as soon as it's looked at.

(defn expire-date
  "Returns a date one week in the future."
  []
  (let [cal (doto (Calendar/getInstance)
              (.add Calendar/WEEK_OF_MONTH 1))
        date (.getTime cal)]
    date))

(defmacro if-logged-in
  "If the user is logged in, executes 'rest'.  Otherwise silently does nothing.  NOTE: if you're using if-logged-in to display HTML, make sure there's a single form for 'rest'.  e.g. do this:
  (if-logged-in (list [:p] [:p]))

  not this:

  (if-logged-in [:p] [:p])"
  [& rest]
  `(if (and *session*
            (:username *session*))
     (try ~@rest)))


;; Form helpers

(defn form-row
  ([label field] (form-row label field nil))
  ([label field name]
     [:div {:class "form-row" :id name}
      [:div.form-label label]
      [:div.form-field field]]))

(defn field
  ([fun name title] (field fun name title ""))
  ([fun name title value]
     (form-row
      (label name (str title ":"))
      (fun name value)
      name)))

(defn submit [name]
  [:div.form-row
   [:div.form-submit
    (submit-button name)]])

;; Page layout stuff

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

(defn block
  "Wraps some forms in HTML for a 'block', which is a series of div that can be used to display a box around some content given a proper CSS setup.  If post is non-nil, the category of the post is used to style the block."
  [post & content]
  (let [cat (if post (:permalink (:category post)) "uncategorized")
        has-cat? (not (= cat "uncategorized"))]
    [:div {:class (str "block block-" cat)}
     [:div.block-top
      (comment (when has-cat?
         (link-to (:url cat) (image (str "/img/" cat ".png")))))]
     [:div.block-content
      [:div (when has-cat?
              {:class cat :style (str "background: url(/img/" cat "-bg.png) no-repeat top right")})
       content]]
     [:div.block-bottom]]))


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

(defn navbar
  "Returns HTML for the navbar (floating side navigation area)."
  []
  [:div.navbar
   [:div.navbar-top]
   [:div#navigation.navbar-content
    [:div.home
     [:h1 (link-to "/" *site-name*)]]
    [:div.categories
     [:h3 "Blog"]
     [:ul
      (map #(li (link-to (:url %) (:name %))) (all-display-categories))]
     [:h3 "Creations"]
     [:ul
      (map #(let [post (get-post %)]
              (li (link-to (:url post) (:title post))))
           (all-toplevel-pages))]
     
     [:h3 "Meta"]
     [:ul
      [:li (link-to "/archives" "Archives")]
      [:li (link-to "/feed" "RSS")]]
     [:h3 "Cows"]
     [:ul
      [:li (link-to "/page/about" "About Me")]]
     [:h3 "Search"]
     [:div.search (form-to [GET "/search"]
                    (text-field :q) (submit-button "Moo"))]
     (if-logged-in
      (list
       [:h3 "You Are Logged In"]
       [:ul
        [:li (link-to "/add" "Add post")]
        [:li (link-to "/moderate-comments" "Moderate")]
        [:li (link-to "/logout" "Logout")]]))]]
   [:div.navbar-bottom]])

(defn footer
  "Returns HTML for the page footer."
  []
  [:div#footer
   "Powered by " (link-to "http://clojure.org" "Clojure")
   " and " (link-to "http://github.com/weavejester/compojure/tree/master" "Compojure")
   " and " (link-to "http://briancarper.net/page/about" "Cows") "."])

(defmacro page
  "Returns top-level HTML for the layout skeleton (main <html> tag, navbar etc.) surrounding `rest."
  [title & rest]
  [{:headers {"Content-Type" "text/html;charset=UTF-8"}}
   `(try
     (str (doctype :xhtml-strict)
          (html [:html {:xmlns "http://www.w3.org/1999/xhtml"}
                 [:head
                  [:title (str ~*site-name* (when ~title (str " :: "  ~title)))]
                  (include-css "/combined.css")
                  (include-js "/combined.js")
                  [:meta {:http-equiv "Content-Type"
                          :content "text/html;charset=utf-8"}]
                  [:link {:rel "alternate" :type "application/rss+xml" :href "/feed"}]
                  ]
                 [:body
                  [:div#doc4.yui-t5
                   [:div#hd]
                   [:div#bd
                    [:div#yui-main
                     [:div#main.yui-b.main
                      ~@rest]]
                    [:div#navbar.yui-b
                     (navbar)]]
                   [:div#ft (footer)]]]])))])

;; Error pages

(defn error
  "Returns HTML for an error page.  If code is given it's used as the HTTP response code (e.g. 403, 404)."
  ([code msg]
     [code (error msg)])
  ([msg]
     (page "Error!"
           (block nil
                  [:h3 (str "ERROR: " msg)]
                  [:p "Sorry, something broke."]
                  [:p "Do you want to go back to the " (link-to "/" "front page") "?"]
                  [:p "Do you want to look through the " (link-to "/archives" "Archives") "?"]))))

(defn error-404
  "Returns HTML for a 404 error page."
  []
  (error 404 "404 - Not Found!"))

;; Login / Logout

;; Note: you must enter a username/password into the DB yourself manually.  The password is salted and SHA-256'ed.  You will have to either calculate this from a commandline, or try to login and fail deliberately and have it tell you the SHA hash it was expecting.

(defn login-page []
  (page "Login"
        (block nil
         (form-to [POST "/login"]
           (field text-field "name" "Username")
           (form-row
            (label "password" "Password")
            (password-field "password"))
           (submit "Login")))))

(defn do-logout []
  (if-logged-in
   (dosync
    (alter *session* dissoc :username)
    (redirect-to "/"))))

(defn do-login [params]
  (dosync
   (if (:logged-in *session*)
     (redirect-to "/")
     (if (get-user {:name (:name *param*)
                    :password (sha-256 (str *password-salt* (:password *param*)))})
       (do
         (alter *session* assoc :username (:name params))
         (redirect-to "/"))
       (error "Login failed!")))))

;; Comments stuff

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

(defn comment-form
  "Returns HTML for a form that a user can use to post a comment."
  [post]
  (form-to [POST (str "/add-comment/" (:id post))]
    (hidden-field "post_id" (:id post))
    (field text-field "author" "Name")
    (field text-field "email" "Email")
    (field text-field "homepage" "URL")
    ;;NOTE: referer is a honeypot.  Only used for anti-spam.
    (field text-field "referer" "How did you find this site?")
    (field markdown-text-area "markdown" "Comment")
    [:div.test-block
     [:div.test
      (image "/img/test.jpg") " "
      [:input {:type "text" :name "test" :id "test" :value "<= Type this word"}]]]
    [:div.clear]
    (submit "Comment")))

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

(declare edit-comment-form)
(defn moderate-comments-page
  "Returns HTML for a list of the last 30 comments, with links to edit them.  Login required."
  []
  (if-logged-in
   (page "Moderate Comments"
         (block nil
                [:h2 "Moderate Comments"]
                (map #(vector :div (edit-comment-form %) [:hr])
                     (all-unapproved-comments)))
         (block nil
                [:h2 "Last 30 Comments"]
                (map moderate-comment-line (take 30 (all-comments)))))))

(defn edit-comment-page
  "Returns HTML for a page to edit a comment.  Login required."
  [id]
  (if-logged-in
   (let [comment (get-comment (bigint id))]
     (page "Edit Comment"
           (block nil
                  (edit-comment-form comment)
                  [:div#preview]
                  (form-to [POST (str "/remove-comment/" (:id comment))]
                    (submit "Delete")))))))

(defn do-edit-comment
  "POST handler for editing a comment.  Login required."
  [id]
  (if-logged-in
   (let [comment (merge (get-comment (bigint id))
                        (assoc *param*
                          :markdown (:markdown *param*)))
         post (get-post (:post_id comment))]
     (edit-comment comment)
     (message "Comment edited")
     (redirect-to (:url post)))))

;; Pages

(defn to-int [x]
  (when x
    (bigint x)))

(defn paginate
  "Returns a certain number of elements of coll based on *posts-per-page* and the current *param*."
  [coll]
  (let [start (or (and (:p *param*)
                       (re-matches #"^\d+$" (:p *param*))
                       (* *posts-per-page* (dec (bigint (:p *param*)))))
                  0)]
    (take *posts-per-page* (drop start coll))))

(defn- param-string [s]
  (str "?" s))

(defn pagenav
  "Returns HTML for a pagination navigation block: a numbered list of links (page 1, page 2 etc.)  This helps us break up huge lists of hundreds of items into smaller paginated lists of a fixed number of items."
  ([coll]
     (pagenav coll param-string))
  ([coll f]
     (let [curr (or (to-int (:p *param*)) 1)
           last-page (inc (bigint (/ (count coll) *posts-per-page*)))]
       (block nil
              [:div.pagenav
               [:span (str "Page " curr " of " last-page)]
               (if (> curr 1)
                 (link-to (f "p=1") "&laquo; First"))
               (if (> curr 1)
                 (link-to (str (f "p=") (dec curr)) "&laquo; Prev"))
               (map (fn [x] (if (= curr x)
                              [:span.num x]
                              [:a.num {:href (str (f "p=") x)} x]))
                    (filter #(and (>= % 1)
                                  (<= % last-page)) (range (- curr 5) (+ curr 5))))

               (if (< curr last-page)
                 (link-to (str (f "p=") (inc curr)) "Next &raquo;"))
               (if (< curr last-page)
                 (link-to (str (f "p=") last-page) "Last &raquo;"))
               [:div.clear]]))))

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

(declare post-form)
(defn new-post-page
  "Returns HTML for a page where a new post or page can be added.  Login required."
  []
  (if-logged-in
   (page "New Post"
         (block nil
                [:h2 "New Post"]
                (post-form "/add")))))

(defn edit-post-page
  "Returns HTML for a page where a post or page can be edited or deleted.  Login required."
  [id]
  (if-logged-in
   (let [post (get-post (bigint id))]
     (page "Edit Post"
           (block nil
                  [:h2 "Edit Post"]
                  (post-form (str "/edit/" (:id post)) post)
                  (form-to [POST (str "/delete/" (:id post))]
                    (submit "Delete")))))))


;; Forms

(defn edit-comment-form [comment]
  (if-logged-in
   (let [post (get-post (:post_id comment))
         url (str "/edit-comment/" (:id comment))]
     (form-to [POST url]
       (field text-field "author" "Name" (:author comment))
       (field text-field "email" "Email" (:email comment))
       (field text-field "homepage" "URL" (:homepage comment))
       (form-row "IP" (:ip comment))
       (field text-area "markdown" "Comment" (:markdown comment))
       (form-row
        (label "approved" "Approved")
        (drop-down "approved"
                   ["0" "1" "2"]
                   (str (:approved comment))))
       (submit "Edit")))))

(defn post-form
  ([url] (post-form url {}))
  ([url post]
     (if-logged-in
      [:div.form
       (form-to [POST url]
         (field text-field "title" "Title" (:title post))
         (field text-field "permalink" "Permalink" (:permalink post))
         (field text-field "created" "Created" (:created post))
         (form-row
          (label "type" "Type:")
          (drop-down "type" ["blog" "page"] (:type post)))
         (form-row
          (label "category_id" "Category:")
          (drop-down "category_id"
                     (map #(vector (:name %) (:id %))
                          (all-categories))
                     (:id (:category post))))
         (field text-field "parent_id" "Parent" (if-let [parent (get-post (:parent_id post))]
                                                     (:permalink parent)))
         (field text-field "all-tags" "Tags" (str-join ", " (map :name (:tags post))))
         (field text-area "markdown" "Content" (:markdown post))
         (submit "Submit"))
       [:h2 "Preview"]
       [:div#preview]])))

;; POST handlers

(defn do-add-post []
  (if-logged-in
   (let [post (add-post *param*)]
     (sync-tags post (re-split #"\s*,\s*" (:all-tags *param*)))
     (redirect-to "/"))))

(defn do-edit-post [id]
  (if-logged-in
   (let [post (merge (get-post (bigint id))
                     (assoc *param*
                       :edited (.getTime (Calendar/getInstance))))]
     (edit-post post)
     (sync-tags post (re-split #"\s*,\s*" (:all-tags *param*)))
     (message "Post Edited")
     (redirect-to (:url post)))))

;; Note, there's some very rudimentary spam filtering here.
;;   1. A honeypot field which is display:hidden from the user; if that field
;;      is non-blank, spam check fails.
;;   2. A CAPTHCA (which is actually a static file).
(defn do-add-comment
  "Handles a POST request to add a comment.  (note: no login required.)"
  [id]
  (let [post (get-post (bigint id))]
    (if (and (empty? (:referer *param*))
             (not (empty? (:test *param*)))
             (re-find #"(?i)cows" (:test *param*)))
      ; Spam test passed
      (if (not (empty? (:markdown *param*)))
        ;; WIN: Post comment, everything OK.
        (do
          (add-comment {
                        :email (:email *param*)
                        :markdown (:markdown *param*)
                        :author (if (empty? (:author *param*))
                                  "Anonymous Cow"
                                  (:author *param*))
                        :homepage (:homepage *param*)
                        :post_id (:post_id *param*)
                        :ip (or (:x-forwarded-for (:headers *request*))
                                (:remote-addr *request*))
                        :approved 1})
          (message "Comment added")
          (redirect-to (:url post)))
        ;; FAIL: 
        (do
          (error-message "Comment failed.  You left your message blank.  :(")
          (redirect-to (:url post))))
      ;; FAIL: Spam test failed, either CAPTCHA or honeypot field.
      (do
        (try
         (add-spam (assoc *param*
                     :post_id (:id post)
                     :ip (or (:x-forwarded-for (:headers *request*))
                             (.getRemoteAddr *request*))))
         (catch Exception e))
        (error-message "Comment failed.  You didn't type the magic word.  :(")
        (redirect-to (:url post))))))

(defn do-remove-post [id]
  (if-logged-in
   (let [post (get-post (bigint id))]
     (remove-post post)))
  (redirect-to "/"))

(defn do-remove-comment [id]
  (if-logged-in
   (let [comment (get-comment (bigint id))
         post (get-post (:post_id comment))]
     (remove-comment comment)
     (redirect-to (:url post)))))

;; RSS

(defmacro rss [title site-url description & body]
  [{:headers {"Content-Type" "text/xml;charset=UTF-8"}}
   `(html "<?xml version=\"1.0\" encoding=\"UTF-8\" ?>"
          [:rss {:version "2.0"
                 :xmlns:content "http://purl.org/rss/1.0/modules/content/"
                 :xmlns:wfw "http://wellformedweb.org/CommentAPI/"
                 :xmlns:dc " http://purl.org/dc/elements/1.1/"}
           [:channel
            [:title ~title]
            [:link ~site-url]
          
            [:description ~description]
            ~@body]])])

(defn rss-item [post]
  (html
   [:item
    [:title (:title post)]
    [:link (str *site-url* (:url post))]
    [:guid (str *site-url* (:url post))]
    [:pubDate (rfc822-date (:created post))]
    [:description (escape-html (:html post))]]))

(defn rss-index []
  (rss
      *site-name*
      *site-url*
      *site-description*
      (map rss-item (take 25 (all-blog-posts)))))

(defn comment-rss [id]
  (if-let [post (get-post id)]
    (rss
        *site-name*
        (str *site-url* (:url post) "#comments")
        *site-name* " Comment Feed for Post " (:title post)
      (map rss-item (:comments post)))
    (error-404 )))

(defn tag-rss [tagname]
  (if-let [tag (get-tag tagname)]
    (rss
        (str *site-name* " Tag: " (:name tag))
        (str *site-url* (:url tag))
        *site-description*
        (map rss-item (take 25 (all-posts-with-tag tag))))
    (error-404 )))

(defn category-rss [catname]
  (if-let [category (get-category catname)]
    (rss
        (str *site-name* " Category: " (:name category))
        (str *site-url* (:url category))
        *site-description* 
      (map rss-item (take 25 (all-posts-with-category category))))
    (error-404 )))