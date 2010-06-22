(ns blog.admin
  "This namespace contains everything admin-related, including
  logging in and out."
  (:use (hiccup [core :only [escape-html]]
                [page-helpers :only [link-to]]
                [form-helpers :only [form-to text-field check-box text-area
                                     password-field drop-down label hidden-field
                                     submit-button]])
        (blog [layout :only [form-row submit-row]]))
  (:require (blog [layout :as layout]
                  [db :as db]
                  [flash :as flash]
                  [error :as error]
                  [link :as link]
                  [time :as time]
                  [util :as util]
                  [config :as config])
            (oyako [core :as oyako])
            (clojure.contrib [string :as s]
                             [sql :as sql])
            (sandbar [stateful-session :as session])
            (ring.util [response :as response])))

(defn login-page
  "Page to let an admin log in."
  []
  {:title "Login"
   :body [:div [:h3 "Log in"]
          (form-to [:post "/login"]
                   (form-row "Username" "username" text-field)
                   (form-row "Password" "password" password-field)
                   (submit-row "Log in"))]})

(defn do-logout
  "Log a user out."
  []
  (session/session-delete-key! :user)
  (flash/message "Logged out.  Goodbye.")
  (response/redirect "/"))

(defn do-login
  "Log a user in, if username/pw are OK."
  [username password]
  (if-let [user (db/check-user username password)]
    (do (session/session-put! :user user) 
        (flash/message (str "Welcome back, " username "."))
        (response/redirect "/admin"))
    (do (flash/error (str "Login failed."))
        (response/redirect "/admin/login"))))

(defn admin-page
  "Main admin control panel."
  []
  {:title "Admin Control Panel"
   :body [:div
          [:h3 "Admin Control Panel"]
          [:ul [:h4 "New Post"]
           [:li (link-to "/admin/add-post" "Write a new Post")]]
          [:ul [:h4 "Manage"]
           [:li (link-to "/admin/edit-posts" "Posts (Edit / Delete)")]
           [:li (link-to "/admin/edit-comments" "Comments (Edit / Delete)")]
           [:li (link-to "/admin/edit-categories" "Categories (Create / Edit / Delete)")]
           [:li (link-to "/admin/edit-tags" "Tags (Create / Edit / Delete)")]]]})

(defn- post-form
  "Render a form to add or edit a post."
  ([target] (post-form target {}))
  ([target post]
     (let [opts (fn [xs] (map #(map % [:title :id]) xs))
           statuses [["draft (hidden from everyone except admins) " "draft"]
                     ["public (everyone can read it) " "public"]]
           types    [["blog (post displayed normally on index pages)" "blog"]
                     ["toplevel (link in the sidebar under PAGES)" "toplevel"]
                     ["page (displayed nowhere unless you link to it yourself)" "page"]]]
       [:div.add-post.markdown
        (form-to [:post target]
                 (when post (hidden-field "id" (:id post)))
                 (form-row "Title" "title"          #(text-field % (:title post)))
                 (form-row "URL" "url"              #(text-field % (:url post)))
                 (form-row "Status" "status"        #(drop-down % statuses (:status post)))
                 (form-row "Type" "type"            #(drop-down % types (:type post)))
                 (form-row "Category" "category_id" #(drop-down %
                                                                (opts (oyako/fetch-all :categories
                                                                                       :order :id))
                                                                (:category_id post)))
                 (when (:tags post)
                   (form-row "Remove Tags" "removetags[]"
                             #(for [tag (:tags post)]
                                [:span (check-box % false (:id tag))
                                 (:url tag)])))
                 (form-row (if (:tags post) "Add Tags" "Tags") "tags"
                           #(text-field %))
                 [:div.info "Tags should be comma-separated and match /"
                  [:code config/TAG-CATEGORY-TITLE-REGEX] "/"]
                 (form-row "Body" "markdown"        #(text-area % (:markdown post)))
                 (submit-row "Submit"))
        (layout/preview-div)])))

(defn add-post-page
  "Page to add a new post."
  []
  {:title "New Post"
   :body (post-form "/admin/add-post")})

(defn edit-posts-page
  "Page that lists post summaries and edit links."
  [& {:keys [page-number]}]
  (let [render (fn [post]
                 [:div
                  [:h4 "#" (:id post) " " (:title post)
                   (layout/status-span post)
                   " [" (link-to (link/url post) "view") "]"
                   (link/edit-link post)]
                  [:div.meta (time/datestr :pretty (:date_created post))
                   " - " (:num_comments post) " comment(s)"]
                  [:p [:em (s/take 250 (:markdown post)) "..."]]
                  [:hr]])]
    {:title "Edit Posts"
     :body [:div
            [:h3 "Edit Posts (sorted by date)"]
            (let [posts (oyako/fetch-all db/posts
                                         :admin? true
                                         :limit config/POSTS-PER-PAGE
                                         :offset (config/page-offset page-number))]
              (layout/render-paginated render posts (count posts) page-number))]}))

(defn edit-post-page
  "Page to edit a post."
  [id]
  (if-let [post (oyako/fetch-one db/posts :id id :admin? true)]
    {:title "Edit Post"
     :body
     [:div [:h3 "Edit Post"]
      (post-form "/admin/edit-post" post)
      [:hr]
      [:h3 "Delete Post"]
      [:p "Be sure you want to do this, there's no going back.  All comments for this post will also be deleted.  Consider marking the post as 'draft' to hide it instead of deleting."]
      (form-to [:post "/admin/delete-post"]
               (hidden-field :id (:id post))
               (submit-button "!!! Delete IRREVOCABLY !!!"))]}))

(defn edit-comments-page
  "Page that lists all comments, with edit links."
  [& {:keys [page-number]}]
  (let [render (fn [comment]
                 [:div
                  [:h4 "#" (:id comment) " "
                   (layout/status-span comment)
                   "[" (link-to (link/url comment) "view") "]"
                   (link/edit-link comment)]
                  [:div.meta
                   [:div "Posted by " (:author comment)
                    (when (:email comment)
                      [:span " &lt;" (:email comment) "&gt;"])
                    (when (:homepage comment)
                      [:span " (" (:homepage comment) ")"])]
                   [:div "Date: " (time/datestr :pretty (:date_created comment))]
                   [:div "IP: " (:ip comment)]]
                  [:p [:em (s/take 150 (:markdown comment))]]
                  [:hr]
                  ])
        comments (oyako/fetch-all db/comments
                                  :admin? true
                                  :limit config/POSTS-PER-PAGE
                                  :offset (config/page-offset page-number)
                                  :order "date_created desc")
        c (oyako/fetch-one (db/count-rows :comments) :admin? true)]
    {:title "Edit Comments"
     :body [:div
            [:h3 "Edit Comments (sorted by date)"]
            (layout/render-paginated render
                                     comments
                                     (:count c)
                                     page-number)]}))

(defn edit-comment-page [id]
  {:title "Edit Comment"
   :body [:div
          (let [comment (oyako/fetch-one :comments :id id)
                posts (oyako/fetch-all :posts :columns [:id :title] :order "date_created desc")]
            (form-to [:post "/admin/edit-comment"]
                     (hidden-field "id" (:id comment))
                     (form-row "Post" "post_id" #(drop-down % (map vector
                                                                   (map :title posts)
                                                                   (map :id posts))
                                                            (:post_id comment)))
                     (form-row "Status" "status" #(drop-down % ["public" "spam"] (:status comment)))
                     (form-row "Author" "author" #(text-field % (:author comment)))
                     (form-row "Email" "email" #(text-field % (:email comment)))
                     (form-row "URL" "homepage" #(text-field % (:url comment)))
                     (form-row "Comment" "markdown" #(text-area % (:markdown comment)))
                     [:div.info "Date: " (:date_created comment)]
                     [:div.info "IP: " (:ip comment)]
                     (submit-row "Submit"))
            [:hr]
            [:h3 "Delete Comment"]
            [:p "Be sure you want to do this, there's no going back.  Consider marking it as spam instead of deleting it, if you want to hide the comment but save it for later (to check the IP address for example)."]
            (form-to [:post "/admin/delete-comment"]
                     (hidden-field :id (:id comment))
                     (submit-button "!!! Delete IRREVOCABLY !!!")))]})

(defn edit-tags-page [& {:keys [page-number]}]
  (let [tags (oyako/fetch-all :tags :order :title)
        tag-drop-down #(drop-down % (map vector
                                     (cons nil (map :title tags))
                                     (cons nil (map :id tags))))]
    {:title "Edit Tags"
     :body [:div
            [:h3 "Create Tag"]
            (form-to [:post "/admin/add-tag"]
                     (form-row "Title" "title" #(text-field %))
                     (form-row "URL" "url" #(text-field %))
                     (submit-row "Create"))
            [:h3 "Delete Tag"]
            (form-to [:post "/admin/delete-tag"]
                     (form-row "Delete Tag" "id" tag-drop-down)
                     (submit-row "!!! Delete IRREVOCABLY !!!"))
            [:h3 "Merge Tags"]
            (form-to [:post "/admin/merge-tags"]
                     (form-row "Delete Tag" "from_id" tag-drop-down)
                     (form-row "Merge into Tag" "to_id" tag-drop-down)
                     (submit-row "Merge"))
            [:h3 "Edit Tags"]
            [:ul (for [x tags]
                   [:li (link-to (str "/admin/edit-tag/" (:id x))
                                 [:strong (:title x)])
                    " (" (:url x) ")"
                    " - " (:num_posts x) " posts"])]]}))

(defn edit-categories-page [& {:keys [page-number]}]
  (let [cats (oyako/fetch-all :categories :order :title)
        cats2 (remove #(= 1 (:id %)) cats)
        cat-drop-down #(drop-down % (map vector
                                         (cons nil (map :title cats2))
                                         (cons nil (map :id cats2))))]
    {:title "Edit Categories"
     :body [:div
            [:h3 "Create Category"]
            (form-to [:post "/admin/add-category"]
                     (form-row "Title" "title" #(text-field %))
                     (form-row "URL" "url" #(text-field %))
                     (submit-row "Create"))
            [:h3 "Delete Category"]
            [:p "Posts in this category will be moved to Uncategorized."]
            (form-to [:post "/admin/delete-category"]
                     (form-row "Delete Category" "id" cat-drop-down)
                     (submit-row "!!! Delete IRREVOCABLY !!!"))
            [:h3 "Merge Categories"]
            (form-to [:post "/admin/merge-categories"]
                     (form-row "Delete Category" "from_id" cat-drop-down)
                     (form-row "Merge into Category" "to_id" cat-drop-down)
                     (submit-row "Merge"))
            [:h3 "Edit Categories"]
            [:ul (for [x cats]
                   [:li (link-to (str "/admin/edit-category/" (:id x))
                                 [:strong (:title x)])
                    " (" (:url x) ")"
                    " - " (:num_posts x) " posts"])]]}))

(defn edit-tag-page [id]
  (let [x (oyako/fetch-one :tags :id id)]
   {:title "Edit Tag"
    :body [:div
           [:h3 "Edit Tag"]
           (form-to [:post "/admin/edit-tag"]
                    (hidden-field "id" (:id x))
                    (form-row "Title" "title" #(text-field %(:title x)))
                    (form-row "URL" "url"     #(text-field % (:url x)))
                    (submit-row "Edit"))]}))

(defn split-tagstring [tagstring]
  (when (re-find #"[^\s]" tagstring)
    (s/split #"\s*,\s*" tagstring)))

(defn- post-from-params [user title url status type category_id markdown]
  {:title title
   :user_id (:id user)
   :url url
   :category_id (util/safe-int category_id)
   :status status
   :type type
   :markdown markdown})

(defn validate-post [uri post tags]
  (error/redirecting-to
   uri
   (s/blank? (:title post)) "Title must not be blank."
   (s/blank? (:url post))   "Url must not be blank."
   (s/blank? (:markdown post)) "You forgot to type a post body."
   (and (not (empty? tags))
        (some #(not (re-matches config/TAG-CATEGORY-TITLE-REGEX %)) tags))
   (str "Invalid tag in '" (escape-html (pr-str tags))
        "'.  Tags should match /" config/TAG-CATEGORY-TITLE-REGEX "/.")
   :else nil))

(defn do-add-post [user title url status type category_id tags markdown]
  (let [new-post (post-from-params user title url
                                   status type category_id
                                   markdown)
        tags (split-tagstring tags)]
    (or
     (validate-post "/admin" new-post tags)
     (try
       (error/with-err-str (oyako/insert :posts new-post))
       (let [post (oyako/fetch-one db/posts :url url)]
         (when tags
           (db/add-tags-to-post post tags))
         (db/update-counts)
         (flash/message "Post added.")
         (response/redirect (link/url post)))
       (catch java.sql.SQLException e
         (error/redirect-and-error "/admin/add-post" (str e)))))))

(defn do-edit-post [user id title url status type category_id addtags markdown removetags]
  (let [post (oyako/fetch-one :posts :id id)
        new-post (merge post (post-from-params user title url
                                               status type category_id
                                               markdown))
        addtags (split-tagstring addtags)]
    (or
     (validate-post (str "/admin/edit-post/" id) new-post addtags)
     (do
       (error/with-err-str (oyako/save new-post))
       (when removetags
         (db/remove-tags-from-post post (util/ensure-vec removetags)))
       (when addtags
         (db/add-tags-to-post post addtags))
       (db/update-counts)
       (flash/message "Post edited.")
       (response/redirect (link/url post))))))

(defn do-delete-post [id]
  (let [post (oyako/fetch-one :posts :id id)]
    (error/with-err-str (oyako/delete post))
    (db/update-counts)
    (flash/message "Post deleted.")
    (response/redirect "/admin")))

(defn do-delete-comment [id]
  (let [comment (oyako/fetch-one :comment :id id)]
    (error/with-err-str (oyako/delete comment))
    (db/update-counts)
    (flash/message "Comment deleted.")
    (response/redirect "/admin")))

(defn do-edit-comment [id post_id status author email homepage markdown]
  (let [comment (oyako/fetch-one :comments :id id :include :post)
        new-comment (merge comment
                           {:post_id (util/safe-int post_id)
                            :status status
                            :author author
                            :email email
                            :homepage homepage
                            :markdown markdown})]
    (error/redirecting-to
     (str "/admin/edit-comment/" id)
     (s/blank? (:author new-comment)) "Author must not be blank."
     (s/blank? (:markdown new-comment)) "You forgot to type a comment body."
     :else (do (error/with-err-str (oyako/save new-comment))
               (db/update-counts)
               (flash/message "Comment .")
               (response/redirect (link/url (:post new-comment)))))))

(defn validate-tag-category [uri x]
  (error/redirecting-to
   uri
   (not (re-matches config/TAG-CATEGORY-TITLE-REGEX (:title x)))
   (str "Title should match regex '" (str config/TAG-CATEGORY-TITLE-REGEX) "'.")

   (not (re-matches config/TAG-CATEGORY-URL-REGEX (:url x)))
   (str "URL should match regex '" (str config/TAG-CATEGORY-URL-REGEX) "'.")))

(defn do-add-tag [title url]
  (let [tag {:title title :url url}]
   (or (validate-tag-category "/admin" tag)
       (do
         (error/with-err-str (oyako/insert :tags ))
         (flash/message "Tag added.")
         (response/redirect "/admin")))))

(defn do-add-category [title url]
  (let [cat {:title title :url url}]
    (or (validate-tag-category cat)
        (do
         (error/with-err-str (oyako/insert :categories))
         (flash/message "Category added.")
         (response/redirect "/admin")))))

(defn do-edit-tag [id title url]
  (let [tag (oyako/fetch-one :tags :id id)
        tag (assoc tag :title title :url url)]
    (or (validate-tag-category tag)
        (do
         (error/with-err-str
           (oyako/save ))
         (flash/message "Tag edited.")
         (response/redirect "/admin")))))

(defn do-edit-category [id title url]
  (let [category (oyako/fetch-one :categories :id id)
        category (assoc category :title title :url url)]
    (or (validate-tag-category category)
        (do
         (error/with-err-str
           (oyako/save ))
         (flash/message "Category edited.")
         (response/redirect "/admin")))))

(defn do-delete-tag [id]
  (let [tag (oyako/fetch-one :tags :id id)]
    (error/with-err-str
      (oyako/delete tag))
    (flash/message "Tag deleted.")
    (response/redirect "/admin")))

(defn do-delete-category [id]
  (let [category (oyako/fetch-one :categories :id id)]
    (error/with-err-str
      (oyako/delete category))
    (db/update-counts)
    (flash/message "Category deleted.")
    (response/redirect "/admin")))

(defn do-merge-tags [from-id to-id]
  (let [from-tag (oyako/fetch-one :tags :id from-id :include :posts)
        to-tag   (oyako/fetch-one :tags :id to-id)]
    (cond (= from-tag to-tag) (flash/error "Can't merge a tag with itself.")
          :else (do (doseq [post (:posts from-tag)]
                      (db/remove-tags-from-post post [(:id from-tag)])
                      (db/add-tags-to-post post [(:title to-tag)]))
                    (oyako/delete from-tag)
                    (db/update-counts)
                    (flash/message "Tags merged.")))
    (response/redirect "/admin")))

(defn do-merge-categories [from-id to-id]
  (let [from-cat (oyako/fetch-one :categories :id from-id :include :posts)
        to-cat   (oyako/fetch-one :categories :id to-id)]
    (cond (= from-cat to-cat) (flash/error "Can't merge a category with itself.")
          :else (do (doseq [post (:posts from-cat)]
                      (oyako/save (assoc post :category_id (:id to-cat))))
                    (oyako/delete from-cat)
                    (db/update-counts)
                    (flash/message "Categories merged.")))
    (response/redirect "/admin")))

