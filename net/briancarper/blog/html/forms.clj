(ns net.briancarper.blog.html.forms
  (:use compojure
        (clojure.contrib str-utils)
        (net.briancarper util)
        (net.briancarper.blog [db :as db]
                              [config :as config]
                              [global :as global])
        (net.briancarper.blog.html [error-pages :as error-pages])))

;; Form helpers

(declare message error-message)

(defn markdown-text-area [name value]
  [:textarea {:id name
              :name name
              :class "resizable markdown"
              :rows 15
              :cols 70} value])

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
       (field markdown-text-area "markdown" "Comment" (:markdown comment))
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
         (field markdown-text-area "markdown" "Content" (:markdown post))
         (submit "Submit"))
       [:h2 "Preview"]
       [:div#preview]])))

;; POST handlers

(defn do-edit-comment
  "POST handler for editing a comment.  Login required."
  [id]
  (if-logged-in
   (let [comment (merge (get-comment (bigint id))
                        (assoc global/*param*
                          :markdown (:markdown global/*param*)))
         post (get-post (:post_id comment))]
     (edit-comment comment)
     (message "Comment edited")
     (redirect-to (:url post)))))

(defn do-add-post []
  (if-logged-in
   (let [post (add-post global/*param*)]
     (sync-tags post (re-split #"\s*,\s*" (:all-tags global/*param*)))
     (redirect-to "/"))))

(defn do-edit-post [id]
  (if-logged-in
   (let [post (merge (get-post (bigint id))
                     (global/*param*))]
     (edit-post post)
     (sync-tags post (re-split #"\s*,\s*" (:all-tags global/*param*)))
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
    (if (and (empty? (:referer global/*param*))
             (not (empty? (:test global/*param*)))
             (re-find #"(?i)cows" (:test global/*param*)))
      ; Spam test passed
      (if (not (empty? (:markdown global/*param*)))
        ;; WIN: Post comment, everything OK.
        (do
          (add-comment {
                        :email (:email global/*param*)
                        :markdown (:markdown global/*param*)
                        :author (if (empty? (:author global/*param*))
                                  "Anonymous Cow"
                                  (:author global/*param*))
                        :homepage (:homepage global/*param*)
                        :post_id (:post_id global/*param*)
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
         (add-spam (assoc global/*param*
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


(defn do-logout []
  [(session-dissoc :username)
   (redirect-to "/")])

(defn do-login [params]
  (dosync
   (if (:logged-in *session*)
     (redirect-to "/")
     (if (get-user {:name (:name global/*param*)
                    :password (sha-256 (str *password-salt* (:password global/*param*)))})
       [(session-assoc :username (:name params))
        (redirect-to "/")]
       (error "Login failed!")))))
