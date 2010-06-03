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
                 [:code config/VALID-TAG-REGEX] "/"]
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
   (s/blank? (:markdown post)) "You forgot to type a post body."
   (and (not (empty? tags))
        (some #(not (re-matches config/VALID-TAG-REGEX %)) tags))
   (str "Invalid tag in '" (escape-html (pr-str tags)) "'.  Tags should match /" config/VALID-TAG-REGEX "/.")))

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
       (or (validate-post "/admin/add-post" new-post tags)
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

(defn ensure-vec [x]
  (if (vector? x) x [x]))

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
               (db/remove-tags-from-post post (ensure-vec removetags)))
             (when addtags
               (db/add-tags-to-post post addtags))
             (merge (response/redirect (link/url post))
                    (flash/message "Post edited."))))))))

(comment

 (defn add-post-page []
   {:title "Add Post"
    :body (post-form "/admin/do-add-post")})

 (defn- validate* [arg]
   `(when (empty? ~arg)
      (die "You left '" (str '~arg) "' blank.  Try again.")))

 (defmacro validate [& args]
   `(do ~@(map validate* args)))

 (defn- post-from-params [ip id title category tags markdown]
   (validate ip id title category tags markdown)
   {:id id
    :title title
    :markdown markdown
    :ip ip
    :category (make-category {:title category})
    :tags (map #(make-tag {:title %})
               (re-split  #"\s*,\s*" tags))})

 (defn do-add-post [& args]
   (add-post (apply post-from-params args))
   [(flash-assoc :message "Post added.")
    (redirect-to "/")])

 (defn edit-post-page [id]
   (if-let [post (get-post id)]
     {:title "Edit Post"
      :body (post-form (str "/admin/do-edit-post/" id) post)}
     [(flash-assoc :error "Post not found.")
      (redirect-to "/")]))

 (defn do-edit-post [old-id & args]
   (edit-post old-id (apply post-from-params args))
   [(flash-assoc :message "Post edited.")
    (redirect-to "/")])

)
