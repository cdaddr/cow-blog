(ns blog.db
  (:use (clojure.contrib def str-utils)
        (blog util tokyocabinet markdown)))

(defonce posts (make-dataref "posts.db"))

(defn- uuid []
  (str (java.util.UUID/randomUUID)))

(defn title-to-id [s]
  (when s
   (->> s
       (re-gsub #"\s" "-")
       (re-gsub #"[^-A-Za-z0-9_]" "")
       .toLowerCase)))

(defn- with-type [type x]
  (with-meta x {:type type}))

(defn- valid-id? [id]
  (re-matches #"^[-A-Za-z0-9_]+$" id))

(defn make-post [post]
  (if (and (valid-id? (post :id))
           (not (empty? (post :markdown))))
    (with-type :post
      (assoc post
        :date (or (post :date) (now))
        :html (markdown-to-html (post :markdown) false)))
    (die "Invalid post data.  You left something blank:  " post)))

(defn make-comment [post c]
  (with-type :comment
    (assoc c
      :id (uuid)
      :post-id (post :id)
      :date (or (c :date) (now))
      :html (markdown-to-html (:markdown c) true))))

(defn make-category [cat]
  (when cat
   (with-type :category
     (assoc cat
       :id (title-to-id (:title cat))))))

(defn make-tag [tag]
  (when tag
   (with-type :tag
     (assoc tag
       :id (title-to-id (:title tag))))))

(defn all-posts []
  (reverse (sort-by :date (vals @posts))))

(defn get-post [id]
  (posts id))

(defn- store-post [post]
  (dosync (alter posts assoc (post :id) (make-post post))))

(defn add-post [post]
  (when (get-post (:id post))
    (die "A post with that ID already exists."))
  (store-post post))

(defn remove-post [id]
  (dosync (alter posts dissoc id)))

(defn edit-post [old-id post]
  (dosync
   (when (not= old-id (:id post))
     (remove-post old-id))
   (store-post post)))

(defn all-categories []
  (sort-by :name
           (set (filter identity
                        (map :category (all-posts))))))

(defn get-category [cat]
  (first (filter #(= (:id %) cat)
                 (all-categories))))

(defn all-tags []
  (sort-by :name
           (set (filter identity
                        (mapcat :tags (all-posts))))))

(defn get-tag [tag]
  (first (filter #(= (:id %) tag)
                 (all-tags))))

(defn all-posts-with-category [category]
  (filter #(= (:category %) category)
          (all-posts)))

(defn all-posts-with-tag [tag]
  (filter #(some #{tag} (:tags %))
          (all-posts)))

(defn get-comments [post]
  (sort-by :date (post :comments)))

(defn add-comment [post comment]
  (dosync
   (let [id (:id post)
         post-in-ref (get-post id)]
    (alter posts assoc id
           (assoc post-in-ref :comments
                  (conj (:comments post-in-ref)
                        (make-comment post comment)))))))

(defn db-watcher-status []
  (agent-errors (:watcher ^posts)))
