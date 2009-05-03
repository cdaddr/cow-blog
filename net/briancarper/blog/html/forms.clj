(ns net.briancarper.blog.html.forms
  (:use compojure
        (clojure.contrib str-utils)
        (net.briancarper util)
        (net.briancarper.blog [db :as db]
                              [config :as config]
                              [global :as global])
        (net.briancarper.blog.html [error-pages :as error-pages])))

;; Form helpers

(defn markdown-text-area [name value]
  [:textarea {:id name
              :name name
              :class "resizable markdown"
              :rows 15
              :cols 70} value])

(defn form-row
  ([label field] (form-row label field nil))
  ([label field name]
     [:div {:class "form-row" :id (str name "-row")}
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
      [:img {:src "/img/test.jpg" :alt " "}]
      [:input {:type "text" :name "test" :id "test" :value "<= Type this word"}]]]
    [:div.clear]
    (submit "Comment")))

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
         (field text-field "created" "Created" (when (:created post) (form-date (:created post))))
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

;;;; POST handlers

;; Comments

(defn do-edit-comment
  "POST handler for editing a comment.  Login required."
  [id]
  (if-logged-in
   (let [comment (merge (get-comment (bigint id))
                        global/*param*)
         post (get-post (:post_id comment))]
     (edit-comment comment)
     [(message "Comment edited.")
      (redirect-to (:url post))])))

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
                        :ip (or ((:headers global/*request*) "x-forwarded-for")
                                (:remote-addr *request*))
                        :approved 1})
          [(message "Comment added.")
           (redirect-to (:url post))])
        ;; FAIL: 
        [(error-message "Comment failed.  You left your message blank.  :(")
         (redirect-to (:url post))])
      ;; FAIL: Spam test failed, either CAPTCHA or honeypot field.
      (do
        (try
         (add-spam (assoc (dissoc global/*param* :id)
                     :post_id (:id post)
                     :ip (or ((:headers global/*request*) "x-forwarded-for")
                             (:remote-addr *request*))))
         (catch Exception e))
        [(error-message "Comment failed.  You didn't type the magic word.  :(")
         (redirect-to (:url post))]))))

(defn do-remove-comment [id]
  (if-logged-in
   (let [comment (get-comment (bigint id))
         post (get-post (:post_id comment))]
     (remove-comment comment)
     [(message "Comment deleted.")
      (redirect-to (:url post))])))

;; Posts

(defn do-add-post []
  (if-logged-in
   (let [post @(add-post (dissoc global/*param* :all-tags))]
     (if (not (empty? (:all-tags global/*param*)))
      (sync-tags post (re-split #"\s*,\s*" (:all-tags global/*param*))))
     [(message "Post added.")
      (redirect-to "/")])))

(defn do-edit-post [id]
  (if-logged-in
   (let [post @(edit-post (merge (get-post (bigint id))
                                 global/*param*))]
     (if (not (empty? (:all-tags global/*param*)))
       (sync-tags post (re-split #"\s*,\s*" (:all-tags global/*param*))))
     [(message "Post Edited")
      (redirect-to (:url post))])))

(defn do-remove-post [id]
  (if-logged-in
   (let [post (get-post (bigint id))]
     (remove-post post)))
  (redirect-to "/"))

;; Login / logout

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
