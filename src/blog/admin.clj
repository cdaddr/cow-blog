(ns blog.admin
  (:use (hiccup [core :only [escape-html]]
                [page-helpers :only [link-to]]
                [form-helpers :only [form-to text-field check-box text-area
                                     password-field drop-down label hidden-field]])
        (blog [layout :only [form-row submit-row]]))
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
                   (form-row "Username" "username" text-field)
                   (form-row "Password" "password" password-field)
                   (submit-row "Log in"))]})

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
           [:li (link-to "/admin/edit-posts" "Posts")]
           [:li (link-to "/admin/edit-comments" "Comments")]
           [:li (link-to "/admin/edit-categories" "Categories")]
           [:li (link-to "/admin/edit-tags" "Tags")]]]})

(defn- post-form
  ([target] (post-form target {}))
  ([target post]
     (let [opts (fn [xs] (map #(map % [:title :id]) xs))]
      [:div.add-post.markdown
       (form-to [:post target]
                (when post (hidden-field "id" (:id post)))
                (form-row "Title" "title"          #(text-field % (:title post)))
                (form-row "URL" "url"              #(text-field % (:url post)))
                (form-row "Status" "status_id"     #(drop-down % (opts (db/statuses))
                                                               (:status_id post)))
                (form-row "Type" "type_id"         #(drop-down % (opts (db/types))
                                                               (:type_id post)))
                (form-row "Category" "category_id" #(drop-down % (opts (db/categories))
                                                               (:category_id post)))
                (when (:tags post)
                  (form-row "Remove Tags" "removetags[]"
                                                   #(for [tag (:tags post)]
                                                      [:span (check-box % false (:url tag))
                                                       (:url tag)])))
                (form-row (if (:tags post) "Add Tags" "Tags") "tags"
                                                   #(text-field %))
                [:div.info "Tags should be comma-separated and match /"
                 [:code config/TAG-CATEGORY-REGEX] "/"]
                (form-row "Body" "markdown"        #(text-area % (:markdown post)))
                (submit-row "Submit"))
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
                   (layout/status-span post)
                   " [" (link-to (link/url post) "view") "]"
                   (link/edit-link post)]
                  [:div.meta (time/datestr :pretty (:date_created post))
                   " - " (count (:comments post)) " comment(s)"]
                  [:p [:em (s/take 250 (:markdown post)) "..."]]
                  [:hr]])]
   {:title "Edit Posts"
    :body [:div
           [:h3 "Edit Posts (sorted by date)"]
           (layout/render-paginated render page-number (db/posts :include-hidden? true))]}))

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
           (when (db/post (:url new-post))
             (error/redirect-and-error
              "/admin" "A post with that URL already exists, can't add another."))
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

(defn do-edit-post [user id title url status_id type_id category_id addtags markdown removetags]
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
                 [:li "#" (:id comment) " "
                  (layout/status-span comment)
                  "[" (link-to (link/url comment) "view") "]"
                  (link/edit-link comment)
                  [:div "Posted by " (:author comment)
                   (when (:email comment)
                     [:span " &lt;" (:email comment) "&gt;"])
                   (when (:homepage comment)
                     [:span " (" (:homepage comment) ")"])]
                  [:div "Date: " (time/datestr :pretty (:date_created comment))]
                  [:div "IP: " (:ip comment)]
                  [:p [:em (s/take 150 (:markdown comment))]]
                  ])]
   {:title "Edit Comments"
    :body [:div
           [:h3 "Edit Comments (sorted by date)"]
           [:ul (layout/render-paginated render page-number (db/comments :include-hidden? true))]]}))

(defn edit-comment-page [id]
  (let [comment (db/comment id)]))


(defn edit-tags-categories-page [which & {:keys [page-number]}]
  (let [[f edit add title] (get {:tags [db/tags "/admin/edit-tag/"
                                        "/admin/add-tag" "Tags"]
                                 :categories [db/categories "/admin/edit-category/"
                                              "/admin/add-category" "Categories"]}
                            which)
        xs (f)]
    {:title title
     :body [:div [:h3 "Edit " title]
            [:ul (for [x xs]
                   [:li (link-to (str edit (:id x))
                                 (str (:title x) "(" (:url x) ")"))])]
            [:h3 "Create New " title]
            (form-to [:post add]
                     (form-row "Title" "title" #(text-field %))
                     (form-row "URL" "url" #(text-field %))
                     (submit-row "Create"))
            ]}))

(defn do-add-tag-category [which title url]
  (let [[table f] (get {:tag [:tags db/tag]
                        :category [:categories db/category]}
                     which)]
    (or (error/redirecting-to "/admin"
                              (f url) (str "A " (name which) " with that url already exists.  Can't add another."))
        (do 
         (db/insert (db/in-table table {:title title :url url}))
         (merge (response/redirect "/admin")
                (flash/message "Added successfully."))))))

(defn edit-tag-category-page [which id]
  (let [[f edit delete title message]
        (get {:tag [db/tag "/admin/edit-tag"
                    "/admin/delete-tag" "Tag"
                    ""]
              :category [db/category "/admin/edit-category"
                         "/admin/delete-category" "Category"
                         "Posts in this category will revert to 'Uncategorized'."]}
             which)
        id (util/safe-int id)
        x (f id)]
    {:title title
     :body [:div
            [:h3 "Edit " title]
            (form-to [:post edit]
                     (hidden-field "xid" (:id x))
                     (form-row "Title" "title" #(text-field %(:title x)))
                     (form-row "URL" "url"     #(text-field % (:url x)))
                     (submit-row "Edit"))
            (when-not (and (= which :category) (= id 1))
              (list
               [:h3 "Delete " title]
               [:p "Are you really, really sure you want to do this?  " message]
               (form-to [:post delete]
                        (hidden-field "xid" (:id x))
                        (submit-row "DELETE IRREVOCABLY"))))]}))

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

(defn do-delete-tag-category [which id]
  (let [table (get {:tag :tags :category :categories}
                   which)
        x (db/bare table (util/safe-int id))]
    (db/delete x)
    (merge (response/redirect "/admin")
           (flash/message "Delete successful."))))
