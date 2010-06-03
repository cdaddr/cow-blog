(ns blog.admin
  (:use (hiccup [core :only [escape-html]]
                [page-helpers :only [link-to]]
                [form-helpers :only [form-to text-field check-box text-area
                                     password-field drop-down label hidden-field]]))
  (:require (blog [layout :as layout]
                  [db :as db]
                  [flash :as flash]
                  [error :as error]
                  [link :as link]
                  [time :as time]
                  [util :as util]
                  [config :as config])
            (clojure.contrib [string :as s]
                             [sql :as sql])
            (sandbar [stateful-session :as session])
            (ring.util [response :as response])))

(defn login-page []
  {:title "Login"
   :body [:div [:h3 "Log in"]
          (form-to [:post "/login"]
                   (layout/form-row text-field "username" "Username")
                   (layout/form-row password-field "password" "Password")
                   (layout/submit-row "Log in"))]})

(defn do-login [username password]
  (if-let [user (db/user username password)]
    (do (session/session-put! :user user)
        (merge (response/redirect "/admin")
               (flash/message (str "Welcome back, " username "."))))
    (merge (response/redirect "/admin/login")
           (flash/error (str "Login failed.")))))

(defn do-logout []
  (session/session-delete-key! :user)
  (merge (response/redirect "/")
         (flash/message "Logged out.  Goodbye.")))

(defn admin-page []
  {:title "Admin Control Panel"
   :body [:div
          [:h3 "Admin Control Panel"]
          [:ul [:h4 "Make new stuff"]
           [:li (link-to "/admin/add-post" "New Post")]]
          [:ul
           [:h4 "Edit stuff"]
           [:li (link-to "/admin/edit-posts" "Edit Posts")]
           [:li (link-to "/admin/edit-comments" "Edit Comments")]
           [:li (link-to "/admin/edit-categories" "Edit Categories")]
           [:li (link-to "/admin/edit-tags" "Edit Tags")]]]})

(defn- post-form
  ([target] (post-form target {}))
  ([target post]
     (let [opts (fn [xs] (map #(map % [:title :id]) xs))]
      [:div.add-post.markdown
       
       (form-to [:post target]
                (when post (hidden-field "id" (:id post)))
                (layout/form-row text-field "title" "Title" (:title post))
                (layout/form-row text-field "url" "URL" (:url post))
                (layout/form-row drop-down "status_id" "Status"
                                 nil
                                 (opts (db/statuses)))
                (layout/form-row drop-down "type_id" "Type"
                                 nil
                                 (opts (db/types)))
                (layout/form-row drop-down "category_id" "Category"
                                 (:category_id post)
                                 (opts (db/categories)))
                (when (:tags post)
                 (layout/form-row #(for [tag %2] [:span (check-box %1 false (:url tag)) (:url tag)])
                                  "removetags[]" "Remove Tags" (:tags post)))
                (layout/form-row text-field "tags"
                                 (if (:tags post) "Add Tags" "Tags"))
                [:div.info "Tags should be comma-separated and match /"
                 [:code config/TAG-CATEGORY-REGEX] "/"]
                (layout/form-row text-area "markdown" "Body" (:markdown post))
                (layout/submit-row "Submit"))
       (layout/preview-div)])))

(defn add-post-page []
  {:title "New Post"
   :body (post-form "/admin/add-post")})

(defn edit-post-page [id]
  (if-let [post (db/post (util/safe-int id))]
    {:title "Edit Post"
     :body (post-form "/admin/edit-post" post)}))


(defn edit-posts-page [& {:keys [page-number]}]
  (let [render (fn [post]
                 [:div
                  [:h4 "#" (:id post) " " (:title post)
                   " [" (link-to (link/url post) "view") "]"
                   " [" (link-to (str "/admin/edit-post/" (:id post)) "edit") "]"]
                  [:div.meta (time/datestr :pretty (:date_created post))
                   " - " (count (:comments post)) " comment(s)"]
                  [:p [:em (s/take 250 (:markdown post)) "..."]]
                  [:hr]])]
   {:title "Edit Posts"
    :body [:div
           [:h3 "Edit Posts (sorted by date)"]
           (layout/render-paginated render page-number (db/posts))]}))

(defn- post-from-params [user title url status_id type_id category_id markdown]
  {:title title
   :user_id (:id user)
   :url url
   :status_id (util/safe-int status_id)
   :type_id (util/safe-int type_id)
   :category_id (util/safe-int category_id)
   :markdown markdown})

(defn validate-post [uri post tags]
  (error/redirecting-to
   uri
   (s/blank? (:title post)) "Title must not be blank."
   (s/blank? (:url post))   "Url must not be blank."
   (db/post (:url post))    "Post with that URL already exists, can't add another."
   (s/blank? (:markdown post)) "You forgot to type a post body."
   (and (not (empty? tags))
        (some #(not (re-matches config/TAG-CATEGORY-REGEX %)) tags))
   (str "Invalid tag in '" (escape-html (pr-str tags)) "'.  Tags should match /" config/TAG-CATEGORY-REGEX "/.")))

(defn split-tagstring [tagstring]
  (when (re-find #"[^\s]" tagstring)
    (s/split #"\s*,\s*" tagstring)))

(defn do-add-post [user title url status_id type_id category_id tags markdown]
  (db/with-db
    (sql/transaction
     (let [new-post (db/in-table :posts
                                 (post-from-params user title url
                                                   status_id type_id category_id
                                                   markdown))
           tags (split-tagstring tags)]
       (or (validate-post "/admin" new-post tags)
           (do
             (try
              (error/with-err-str (db/insert new-post))
              (let [post (db/post url)]
                (when tags
                  (db/add-tags-to-post post tags))
                (merge (response/redirect (link/url post))
                       (flash/message "Post added.")))
              (catch java.sql.SQLException e
                (error/redirect-and-error "/admin/add-post" (str e))))))))))

(defn do-edit-post [id user title url status_id type_id category_id addtags removetags markdown]
  (db/with-db
    (sql/transaction
     (let [post (db/bare :posts (util/safe-int id))
           new-post (merge post (post-from-params user title url
                                                  status_id type_id category_id
                                                  markdown))
           addtags (split-tagstring addtags)]
       (or (validate-post (str "/admin/edit-post/" id) new-post addtags)
           (do
             (error/with-err-str (db/update new-post))
             (when removetags
               (db/remove-tags-from-post post (util/ensure-vec removetags)))
             (when addtags
               (db/add-tags-to-post post addtags))
             (merge (response/redirect (link/url post))
                    (flash/message "Post edited."))))))))


(defn edit-comments-page [& {:keys [page-number]}]
  (let [render (fn [comment]
                 [:li "#" (:id comment)
                  "[" (link-to (link/url comment) "view") "]"
                  "[" (link-to (str "/admin/edit-comment/" (:id comment)) "edit") "]"
                  [:div "Posted by " (:author comment)
                   " [" (:email comment) "]"
                   " (" (:homepage comment) ")"
                   " on " (time/datestr :pretty (:date_created comment))]
                  [:p [:em (s/take 150 (:markdown comment))]]
                  ])]
   {:title "Edit Comments"
    :body [:div
           [:h3 "Edit Comments (sorted by date)"]
           [:ul (layout/render-paginated render page-number (db/comments))]]}))


(defn edit-tags-categories-page [which & {:keys [page-number]}]
  (let [[f uri title] (get {:tags [db/tags "/admin/edit-tag/" "Edit Tags"]
                       :categories [db/categories "/admin/edit-category/" "Edit Categories"]}
                            which)
        xs (f)]
    {:title title
     :body [:div
            [:h3 title]
            [:ul
             (for [x xs]
               [:li (link-to (str uri (:id x))
                             (str (:title x) "(" (:url x) ")"))])]]}))

(defn edit-tag-category-page [which id]
  (let [[f uri title] (get {:tag [db/tag "/admin/edit-tag" "Edit Tag"]
                            :category [db/category "/admin/edit-category" "Edit Category"]}
                          which)
        x (f (util/safe-int id))]
    {:title title
     :body [:div
            [:h3 title]
            (form-to [:post uri]
                     (hidden-field "xid" (:id x))
                     (layout/form-row text-field "title" "Title" (:title x))
                     (layout/form-row text-field "url" "URL" (:url x))
                     (layout/submit-row "Submit"))]}))

(defn validate-tag-category [uri x]
  (error/redirecting-to
   uri
   (not (re-matches config/TAG-CATEGORY-REGEX (:title x)))
   (str "Title should match regex '" (str config/TAG-CATEGORY-REGEX) "'.")

   (not (re-matches config/TAG-CATEGORY-URL-REGEX (:url x)))
   (str "URL should match regex '" (str config/TAG-CATEGORY-URL-REGEX) "'.")))

(defn do-edit-tag-category [which id title url]
  (let [[f] (get {:tag [:tags]
                  :category [:categories]}
                 which)
        x (merge (db/bare f (util/safe-int id))
                 {:title title :url url})]
    (or (validate-tag-category "/admin" x)
        (do
          (error/with-err-str (db/update x))
          (merge (response/redirect (link/url x))
                 (flash/message "Edit successful."))))))
