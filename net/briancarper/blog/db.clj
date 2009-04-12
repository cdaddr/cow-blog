(ns net.briancarper.blog.db
  (:use (net.briancarper.blog [config :as config])
        (net.briancarper util
                         markdown
                         [crud :as crud])
        (clojure.contrib sql str-utils pprint)
        (compojure html))
  (:import (java.util Calendar)))

(defonce data (ref {}))

(defn init-all []
  (init-from-db config/db data
                ::comments
                ::tags
                ::post_tags
                ::categories
                ::users
                ::spam
                ::posts
                ::posts))

(defn- csort
  "Reverse-sort a coll by creation date"
  [coll]
  (reverse (sort-by :created coll)))

(defmacro deftable [singular plural]
  (let [conditions 'conditions
        obj (symbol singular)
        table (keyword (name (ns-name *ns*)) plural)]
    `(do
       (defn ~(symbol (str "get-" singular))
         ~(str "Returns a single " singular " matching some conditions.  Throws an exception if more than one matching record is found.")
         [~conditions]
         (fetch data ~table ~conditions))
       (defn ~(symbol (str "all-" plural))
         ~(str "Returns all " plural " matching some conditions.  If no conditions are given, returns all " plural ".")
         ([] (csort (fetch-all data ~table)))
         ([~conditions]
            (csort (fetch-all data ~table ~conditions))))
       (defn ~(symbol (str "add-" singular))
         ~(str "Adds a " singular " to the dataref and DB.")
         [~obj]
         (create data ~obj db ~table))
       (defn ~(symbol (str "remove-" singular))
         ~(str "Removes a " singular " from the dataref and DB.")
         [~obj]
         (delete data ~obj db ~table))
       (defn ~(symbol (str "edit-" singular))
         ~(str "Updates a " singular " in the dataref and DB.")
         [~obj]
         (update data ~obj db ~table))
       (defn ~(symbol (str "get-or-add-" singular))
         ~(str "Fetches a " singular " from the dataref if it exists; otherwise adds it to the dataref and DB.")
         [~obj]
         (or (~(symbol (str "get-" singular)) ~obj)
             @(~(symbol (str "add-" singular)) ~obj))))))

(deftable "post" "posts")
(deftable "comment" "comments")
(deftable "tag" "tags")
(deftable "user" "users")
(deftable "post_tag" "post_tags")
(deftable "category" "categories")

(defn all-blog-posts
  "Returns a seq of all posts with type 'page'."
   []
  (csort (all-posts {:type "blog"})))

(defn all-pages
  "Returns a seq of all posts with type 'post'."
  []
  (csort (all-posts {:type "page"})))

(defn post-comments [post]
  (all-comments {:post_id (:id post)}))

(defn post-tags [post]
  (map #(get-tag (:tag_id %))
       (all-post_tags {:post_id (:id post)})))

(defn post-category [post]
  (get-category {:post_id (:id post)}))

(defn post-post_tags [post]
  (all-post_tags {:post_id (:id post)}))

(defmethod crud/fetch ::posts [data table s]
  (get-post {:permalink s}))
(defmethod crud/fetch ::tags [data table s]
  (get-tag {:permalink s}))
(defmethod crud/fetch ::categories [data table s]
  (get-category {:permalink s}))

(defn all-posts-with-tag
  "Returns a seq of all posts with some tag."
  [tag]
  (all-posts #(some #{tag} (post-tags %))))

(defn all-posts-with-category
  "Returns a seq of all posts belonging to some category."
  [cat]
  (all-posts #(= (:category_id %) (:id cat))))

;; URL-generation

(defmulti make-url #(or (:type %) (:table (meta %))))

(defmethod make-url "blog" [post]
  (str "/blog/" (:permalink post)))

(defmethod make-url "page" [post]
  (if-let [parent (get-post (:parent_id post))]
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

(defmethod after-db-read ::posts [post]
  (assoc post
    :url (make-url post)
    :parent (if-let [id (:parent_id post)] (get-post id))))

(defmethod after-delete ::posts [post]
  (dorun (map remove-comment (post-comments post)))
  (dorun (map remove-post_tag (post-post_tags post))))

;; COMMENTS

(defn- normalize-homepage [s]
  (when s
   (let [s (re-gsub #"^\s+|\s*$" "" s)]
     (cond
       (empty? s) nil
       (re-find #"(?i)^http://" s) s
       :else (str "http://" s)))))

(defmethod before-save ::comments [c]
  (assoc c
    :html (markdown-to-html (:markdown c) true)
    :homepage (normalize-homepage (escape-html (:homepage c)))
    :email (escape-html (:email c))
    :author (escape-html (:author c))))

(defmethod after-db-read ::comments [c]
  (let [gravatar-hash (.asHex (doto (com.twmacinta.util.MD5.)
                                (.Update (or (:email c) (:ip c)) nil)))
        gravatar (str "http://gravatar.com/avatar/" gravatar-hash ".jpg?d=identicon")]
    (assoc c
      :avatar gravatar)))

;; POST_TAGS

(defmethod after-delete ::post_tags [pt]
  (let [tag (get-tag (:tag_id pt))]
    (if (empty? (all-posts-with-tag tag))
      (remove-tag tag))))

;; TAGS

(defn- tagname-to-permalink [name]
  (.toLowerCase (re-gsub #"\s" "-" name)))

(defmethod before-save ::tags [tag]
  (assoc tag :permalink (tagname-to-permalink (:name tag))))

(defmethod after-db-read ::tags [tag]
  (assoc tag :url (make-url tag)))

;; CATEGORIES

(defmethod after-db-read ::categories [cat]
  (assoc cat :url (make-url cat)))

;; Additional public accessor functions

(defn add-tag-to-post
  "Given a post and a tag name, fetches or creates the tag, then puts an entry in post_tags linking the two. "
  [post tag-name]
  (let [post (get-post (:id post))
        tag (get-or-add-tag {:name tag-name})]
    (get-or-add-post_tag {:post_id (:id post)
                          :tag_id (:id tag)})))

(defn remove-tag-from-post
  "Given a post and a tag name, removes the matching tag from the post."
  [post tag-name]
  (let [post (get-post (:id post))
        tag (get-tag {:name tag-name})
        post_tag (get-post_tag {:post_id (:id post)
                                :tag_id (:id tag)})]
    (remove-post_tag post_tag)))

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
  (all-comments #(= 0 (:approved %))))

(defn all-tags-with-counts
  "Returns a seq of two-item pairs: a count of posts for a tag, and the tag itself."
  []
  (loop [counts {}
         tags (mapcat post-tags (all-posts))]
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
    (all-posts #(post-matches % terms))))

(defn sync-tags
  "Given a post, and a list of tag names (seq of Strings), synchronizes the post to have exactly those tags, adding or removing as needed."
  [post taglist]
  (let [new-tags (into #{} (map #(get-or-add-tag {:name %}) taglist))
        old-tags (into #{} (post-tags post))
        tags-to-add (clojure.set/difference new-tags old-tags)
        tags-to-delete (clojure.set/difference old-tags new-tags)]
    (comment (dorun (map pprint [new-tags
                         old-tags
                         tags-to-add
                         tags-to-delete])))
    (dorun (map #(add-tag-to-post post (:name %)) tags-to-add))
    (dorun (map #(remove-tag-from-post post (:name %)) tags-to-delete))))

(defn init-db
  "Initializes the database (creates empty tables)."
  []
  (with-connection db
   (let [string "VARCHAR(255)"
         id [:id :int "AUTO_INCREMENT" "NOT NULL" "PRIMARY KEY"]]
     (innodb-create-table :posts
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
     (innodb-create-table :comments
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
     (innodb-create-table :spam
                   id
                   [:post_id :int "NOT NULL"]
                   [:author string]
                   [:homepage string]
                   [:email string]
                   [:ip string]
                   [:markdown :longtext]
                   [:created :timestamp "DEFAULT CURRENT_TIMESTAMP"])
     (innodb-create-table :users
                   id
                   [:name string]
                   [:password string])
     (innodb-create-table :categories
                   id
                   [:name string]
                   [:permalink string])
     (innodb-create-table :post_tags
                   id
                   [:post_id :int "NOT NULL"]
                   [:tag_id :int "NOT NULL"])
     (innodb-create-table :tags
                   id
                   [:permalink string "NOT NULL"]
                   [:name string]))))

(defn- add-user [username password]
  (create {:name username
           :password (sha-256 (str *password-salt* password))}
          db ::users))

(comment
 (defn- refresh-html
   "Brute-force refresh of the Markdown-generated HTML for a list of objects."  
   [objs]
   (dorun (map #(update (assoc % :html (markdown-to-html (:markdown %) false)))
               objs)))

 (defn- refresh-html-all []
   "Brute-force refresh of the Markdown-generated HTML for all posts and comments."  
   (dorun (map refresh-markdown [(fetch-all ::posts) (fetch-all ::comments)]))))

