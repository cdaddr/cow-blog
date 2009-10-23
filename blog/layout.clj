(ns blog.layout
  (:use (compojure.html gen page-helpers form-helpers)
        (clojure.contrib pprint)
        (blog config db util)))

(defn preview-div []
  [:div
   [:h4 "Preview"]
   [:div#preview]])

(defmulti url type)

(defmethod url :tag [tag]
  (str "/tag/" (tag :id)))

(defmethod url :category [cat]
  (str "/category/" (cat :id)))

(defmethod url :post [post]
  (str "/post/" (post :id)))

(defmethod url :default [x]
  (die "Don't know how to make a url out of a " (type x)))

(defn link [x]
  (link-to {:class (str (type x) "-link")}
           (url x)
           (x :title)))

(defn post-comments-link [post]
  (when post
    (link-to (str "/post/" (post :id) "#comments")
             (cl-format nil "~a Comment~:*~[s~;~:;s~]"
                        (count (post :comments))))))

(defn- nav [admin]
  [:div.navigation
   [:ul "Categories"
    (map #(vector :li (link %)) (all-categories))]
   [:ul "Tags"
    (map #(vector :li (link %)) (all-tags))]
   [:ul "Meta"
    (comment "TODO"
      [:li (link-to "/archives" "Archives")]
      [:li (link-to "/tag-cloud" "Tag Cloud")])
    [:li (link-to "/rss.xml" "RSS")]]
   (if admin
     [:div.admin
      [:ul "Hello, " admin
       [:li (link-to "/admin/add-post" "Add Post")]
       [:li (form-to [:post "/admin/logout"]
              (submit-button "Log out"))]]
      [:ul "DB Status: "
       [:li [:strong (if-let [errors (db-watcher-status)]
                       [:div [:span.error "ERROR!"]
                        [:div errors]]
                       [:span.message "OK"])]]]]
     [:ul "Log in?"
      [:li (link-to "/admin/login" "Log in")]])])

(defn messages [flash]
  (list
   (when-let [e (:error flash)]
     [:div.error e])
   (when-let [m (:message flash)]
     [:div.message m])))

(defn page [admin title flash session & body]
  (html
   [:html
    [:head
     [:title SITE-TITLE " - " title]
     (include-css "/css/style.css")
     (include-js "/js/combined.js")] ;; magic
    [:body
     [:div#page
      [:div#header [:h1 (link-to SITE-URL SITE-TITLE)]]
      [:div#nav (nav admin)]
      [:div#body.body
       (messages flash)
       body]
      [:div#footer
       "Powered by "
       (link-to "http://clojure.org" "Clojure") " and "
       (link-to "http://github.com/weavejester/compojure" "Compojure") " and "
       (link-to "http://briancarper.net" "Cows") "."]]]]))
