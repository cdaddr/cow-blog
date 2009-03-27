(ns net.briancarper.blog.db
  (:use (net.briancarper.blog config)
        (net.briancarper util
                         markdown
                         [crud :exclude [data db]])
        (clojure.contrib sql str-utils)
        (compojure html))
  (:import (java.util Calendar)))

(defonce data (ref {}))

(defmacro with-crud [& rest]
  `(binding [net.briancarper.crud/db net.briancarper.blog.config/db
             net.briancarper.crud/data data]
     ~@rest))

;; These require with-crud

(defn init-all []
  (with-crud
   (dorun (map #(do (prn %) (init %))
               [::comments ::tags ::post_tags ::categories ::users ::posts ::posts])))
  'ok)

(defn normalize-tagname [name]
  (if (string? name)
    (.toLowerCase (re-gsub #"\s" "-" name))
    name))

(defn normalize-homepage [s]
  (when s
   (let [s (re-gsub #"^\s+|\s*$" "" s)]
     (cond
       (empty? s) nil
       (re-find #"(?i)^http://" s) s
       :else (str "http://" s)))))

(defn- csort
  "Reverse-sort a coll by creation date"
  [coll]
  (reverse (sort-by :created coll)))

;; These are simple wrapper methods, in case I ever decide I don't want to use my CRUD library,
;; I won't have to rewrite my whole HTML lib.

(defn all-posts
  "Returns a seq of all posts (blog posts + pages)."
  []
  (with-crud
   (csort (fetch-all ::posts))))

(defn all-blog-posts
  "Returns a seq of all posts with type 'page'."
   []
  (with-crud
   (csort (fetch-all ::posts {:type "blog"}))))

(defn all-pages
  "Returns a seq of all posts with type 'post'."
  []
  (with-crud
   (csort (fetch-all ::posts {:type "page"}))))

(defn all-comments []
  (with-crud (csort (fetch-all ::comments))))
(defn all-tags []
  (with-crud (sort-by :name (fetch-all ::tags))))
(defn all-categories []
  (with-crud (sort-by :name (fetch-all ::categories))))
(defn all-post_tags [] (fetch-all ::post_tags))

(defn- string-fetch
  "Helper method - fetching via a string should look for permalinks."
  [table s]
  (with-crud
   (if (string? s)
     (fetch table {:permalink s})
     (throw (Exception. (str "Invalid fetch for " table))))))

(defmethod fetch ::posts [table s] (string-fetch table s))
(defmethod fetch ::tags [table s] (string-fetch table s))
(defmethod fetch ::categories [table s] (string-fetch table s))

(defn get-post [x] (with-crud (fetch ::posts x)))
(defn get-comment [x] (with-crud (fetch ::comments x)))
(defn get-category [x] (with-crud (fetch ::categories x)))
(defn get-tag [x] (with-crud (fetch ::tags (normalize-tagname x))))
(defn get-user [x] (with-crud (fetch ::users x)))
(defn get-post_tag [x] (with-crud (fetch ::post_tags x)))

(defn get-tags-for-post
  "Returns a seq of tags for a given post."
  [post]
  (with-crud
   (doall
    (map #(get-tag (:tag_id %))
         (fetch-all ::post_tags {:post_id (:id post)})))))

(defn all-posts-with-tag
  "Returns a seq of all posts with some tag."
  [tag]
  (let [tag (get-tag tag)]
    (filter #(some #{tag} (:tags %))
            (all-posts))))

(defn all-posts-with-category
  "Returns a seq of all posts belonging to some category."
  [cat]
  (let [cat (get-category cat)]
    (filter #(= (:category_id %) (:id cat))
            (all-blog-posts))))


;; URL-generation

(defmulti make-url #(or (:type %) (:table %)))

(defmethod make-url "blog" [post]
  (str "/blog/" (:permalink post)))

(defmethod make-url "page" [post]
  (if-let [parent (fetch ::posts (:parent_id post))]
    (str (make-url parent) "/" (:permalink post))
    (str "/page/" (:permalink post))))

(defmethod make-url ::categories [cat]
  (str "/category/" (:permalink cat)))

(defmethod make-url ::tags [tag]
  (str "/tag/" (:permalink tag)))

;; HOOKS - these tie posts, tags, comments, categories together to make sure everything
;; stays up-to-date in the data ref and DB.

;; POSTS

(defmethod before-save ::posts [post]
  (assoc post
    :html (markdown-to-html (:markdown post) false)
    :parent_id (if-let [post (get-post (:parent_id post))]
                 (:id post))))

(defmethod before-update ::posts [post]
  (assoc post :edited (.getTime (Calendar/getInstance))))

(defmethod before-ref ::posts [post]
  (assoc post
    :url (make-url post)
    :parent (if-let [id (:parent_id post)] (get-post id))
    :comments (sort-by :created
                       (fetch-all ::comments {:post_id (:id post)
                                          :approved 1}))
    :category (fetch ::categories (:category_id post))
    :post_tags (fetch-all ::post_tags {:post_id (:id post)})
    :tags (get-tags-for-post post)))

(defmethod after-delete ::posts [post]
  (dorun (map delete (:comments post)))
  (dorun (map delete (:post_tags post))))

;; COMMENTS

(defmethod before-save ::comments [c]
  (assoc c
    :html (markdown-to-html (:markdown c) true)
    :homepage (normalize-homepage (escape-html (:homepage c)))
    :email (escape-html (:email c))
    :author (escape-html (:author c))))

(defmethod before-ref ::comments [c]
  (let [gravatar-hash (.asHex (doto (com.twmacinta.util.MD5.)
                                (.Update (or (:email c) (:ip c)) nil)))
        gravatar (str "http://gravatar.com/avatar/" gravatar-hash ".jpg?d=identicon")]
    (assoc c
      :avatar gravatar)))

(defmethod after-all ::comments [c]
  (if-let [post (get-post (:post_id c))]
    (reload post)))

;; POST_TAGS

(defmethod after-delete ::post_tags [pt]
  (if-let [post (get-post (:post_id pt))]
    (reload post))
  (let [tag (get-tag (:tag_id pt))]
    (if (empty? (all-posts-with-tag tag))
      (delete tag))))

(defmethod after-all ::post_tags [pt]
  (if-let [post (get-post (:post_id pt))]
    (reload post)))

;; TAGS

(defmethod before-ref ::tags [tag]
  (assoc tag
    :url (make-url tag)))

(defmethod after-all ::tags [tag]
  (dorun (map reload (all-posts-with-tag tag)))
  tag)

;; CATEGORIES

(defmethod before-ref ::categories [cat]
  (assoc cat
    :url (make-url cat)))

(defmethod after-all ::categories [cat]
  (dorun (map reload (all-posts-with-category cat)))
  cat)

;; These will interact with HTML forms.

(defn add-post
  "Given a hash-map of column => values, creates a new post in the DB and data ref."
  [post]
  (with-crud
   (create ::posts post)))

(defn add-comment
  "Creates a comment in the DB and data ref."
  [post comment]
  (with-crud
   (create ::comments comment)))

(defn add-spam [spam]
  "Add a spam entry to the spam table.  NOTE: not included in the data refs!"
  (with-crud
   (create :spam spam false)))

(defn edit-post
  "Update a post in the DB and data ref."
  [post]
  (with-crud
   (update post)))

(defn edit-comment
  "Update a comment in the DB and data ref."
  [comment]
  (with-crud
   (update comment)))

(defn remove-post
  "Deletes a post and all related comments from the DB and data ref.  If this is the only post with a certain tag, also deletes the tag."
  [post]
  (with-crud
   (delete post)))

(defn remove-comment
  "Deletes a comment from the DB and data ref."
  [comment]
  (with-crud
   (delete comment)))


;; Additional public accessor functions

(defn get-or-add-tag
  "If a tag with a certain name exists, returns it; otherwise creates it.  name should be the tag's display name."
  [name]
  (with-crud
   (if (string? name)
     (let [permalink (normalize-tagname name)]
       (or (get-tag permalink)
           (create ::tags {:permalink permalink
                           :name name})))
     name)))

(defn add-tag-to-post
  "Given a post and a tag name, fetches or creates the tag, then puts an entry in post_tags linking the two. "
  [post name]
  (with-crud
   (let [post (get-post (:id post))
         tag (get-or-add-tag name)]
     (fetch-or-create ::post_tags {:post_id (:id post)
                                :tag_id (:id tag)}))))

(defn remove-tag-from-post
  "Given a post and a tag name, removes the matching tag from the post."
  [post tag]
  (with-crud
   (let [post (get-post (:id post))
         tag (get-tag tag)
         post_tag (get-post_tag {:post_id (:id post)
                                 :tag_id (:id tag)})]
     (delete post_tag))))

(defn all-display-categories
  "Returns a seq of all categories that are meant to be listed in the categories list."
  []
  (filter #(> (:id %) 1)
          (all-categories)))

(defn all-toplevel-pages
  "Returns a seq of all pages whose parent is nil, sorted by title."
  []
  (:sort-by :title
            (filter #(nil? (:parent_id %))
                    (all-pages))))

(defn all-unapproved-comments
  "Returns a seq of all comments which have not been approved."
  []
  (filter #(= 0 (:approved %))
          (all-comments)))

(defn all-tags-with-counts
  "Returns a seq of two-item pairs: a count of posts for a tag, and the tag itself."
  []
  (loop [counts {}
         tags (mapcat :tags (all-posts))]
    (if (seq tags)
      (let [tag (first tags)
            c (or (counts tag) 0)]
        (recur (assoc counts tag (inc c))
               (rest tags)))
      counts)))

(defn- post-matches
  "Given a seq of search terms (Strings), returns true if a post matches some search terms."
  [post terms]
  (let [res (map re-pattern terms)]
    (every? #(re-find % (:markdown post))
            res)))

(defn search-posts
  "Returns all posts matching a seq of search terms (Strings)."
  [terms]
  (let [terms (map re-pattern (re-split #"\s+" terms))]
    (filter #(post-matches % terms) (all-posts))))

(defn sync-tags
  "Given a post, and a list of tag names (seq of Strings), synchronizes the post to have exactly those tags, adding or removing as needed."
  [post taglist]
  (let [new-tags (into #{} (map get-or-add-tag taglist))
        old-tags (into #{} (:tags post))
        tags-to-add (clojure.set/difference new-tags old-tags)
        tags-to-delete (clojure.set/difference old-tags new-tags)]
    (comment (dorun (map println [(ensure-str-list taglist)
                                  new-tags
                                  old-tags
                                  tags-to-add
                                  tags-to-delete])))
    (dorun (map #(add-tag-to-post post %) tags-to-add))
    (dorun (map (partial remove-tag-from-post post ) tags-to-delete))))

(defn init-db
  "Initializes the database (creates empty tables)."
  []
  (with-crud
   (with-db
    (let [string "VARCHAR(255)"
          id [:id :int "AUTO_INCREMENT" "NOT NULL" "PRIMARY KEY"]]
      (create-table :posts
                    id
                    [:permalink string "NOT NULL" "UNIQUE KEY"]
                    [:type string "NOT NULL"]
                    [:title string]
                    [:markdown :longtext]
                    [:html :longtext]
                    [:parent_id :int]
                    [:category_id :int "NOT NULL" "DEFAULT 1"]
                    [:created :timestamp "DEFAULT CURRENT_TIMESTAMP"]
                    [:edited :datetime "DEFAULT NULL"])
      (create-table :comments
                    id
                    [:post_id :int "NOT NULL"]
                    [:author string]
                    [:homepage string]
                    [:email string]
                    [:ip string]
                    [:markdown :longtext]
                    [:html :longtext]
                    [:created :timestamp "DEFAULT CURRENT_TIMESTAMP"]
                    [:edited :datetime "DEFAULT NULL"]
                    [:approved :integer "DEFAULT 0"])
      (create-table :spam
                    id
                    [:post_id :int "NOT NULL"]
                    [:author string]
                    [:homepage string]
                    [:email string]
                    [:ip string]
                    [:markdown :longtext]
                    [:created :timestamp "DEFAULT CURRENT_TIMESTAMP"])
      (create-table :users
                    id
                    [:name string]
                    [:password string])
      (create-table :categories
                    id
                    [:name string]
                    [:permalink string])
      (create-table :post_tags
                    id
                    [:post_id :int "NOT NULL"]
                    [:tag_id :int "NOT NULL"])
      (create-table :tags
                    id
                    [:permalink string "NOT NULL"]
                    [:name string])))))

(defn- add-user [username password]
  (with-crud
   (create ::users {:name username
                    :password (sha-256 (str *password-salt* password))})))

(comment
 (defn- refresh-html
   "Brute-force refresh of the Markdown-generated HTML for a list of objects."  
   [objs]
   (dorun (map #(update (assoc % :html (markdown-to-html (:markdown %) false)))
               objs)))

 (defn- refresh-html-all []
   "Brute-force refresh of the Markdown-generated HTML for all posts and comments."  
   (dorun (map refresh-markdown [(fetch-all ::posts) (fetch-all ::comments)]))))

