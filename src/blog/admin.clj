(ns blog.admin
  (:use (hiccup [form-helpers :only [form-to text-field password-field]]))
  (:require (blog [layout :as layout]
                  [db :as db]
                  [flash :as flash]
                  [error :as error])
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
        (merge (response/redirect "/admin/cp")
               (flash/message (str "Welcome back, " username "."))))
    (merge (response/redirect "/admin/login")
           (flash/error (str "Login failed.")))))

(defn do-logout [])

(defn cp-page []
  )

(comment


 (defn- generate-user [username password]
   {:username username :password (sha-256 (str PASSWORD-SALT password))})

 (defn- validate-user [username password]
   (= (generate-user username password) ADMIN-USER))

 (defn do-login [username password]
   (if (validate-user username password)
     (do (session/session-put! :user user)
         [(flash-assoc :message "Logged in OK.")
          (redirect-to "/")])
     [(flash-assoc :error "Login failed.")
      (redirect-to "/admin/login")]))

 (defn do-logout []
   [(session-dissoc :username)
    (redirect-to "/")])

 (defn- post-form
   ([target] (post-form target {}))
   ([target post]
      [:div.add-post.markdown
       [:p "Leave nothing blank!"]
       [:p "Tags should be comma-separated."]
       (form-to [:post target]
                (form-row text-field "title" "Title" (:title post))
                (form-row text-field "id" "ID" (:id post))
                (form-row text-field "category" "Category" (:title (:category post)))
                (form-row text-field "tags" "Tags" (str-join #", " (map :title (:tags post))))
                (form-row text-area "markdown" "Body" (:markdown post))
                (submit-row "Submit"))
       (preview-div)]))

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
