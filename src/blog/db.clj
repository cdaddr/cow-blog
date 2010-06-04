(ns blog.db
  (:require (clojure.contrib [sql :as sql]
                             [string :as s])
            (net.briancarper [oyako :as oyako])
            (blog [config :as config]
                  [util :as util]
                  [gravatar :as gravatar]
                  [time :as time]
                  [markdown :as markdown]))
  (:refer-clojure :exclude [comment]))

(defn- table-meta [x]
  (:table (meta x)))

(defn in-table [table x]
  (with-meta x {:table table}))

(defmacro with-table [[table x] & body]
  `(with-db
     (let [x# ~x]
       (if-let [~table (:table (meta x#))]
         (do ~@body)
         (util/die "Can't determine the table for " x#)))))

(defn sha-256 [s]
  (let [md (java.security.MessageDigest/getInstance "SHA-256")]
    (.update md (.getBytes s))
    (s/join ""
            (mapcat #(Integer/toHexString (bit-and 0xff %))
                    (into [] (.digest md))))))

(def schema
     (oyako/make-datamap config/DB
      [:posts
       [belongs-to :categories as :category]
       [belongs-to :statuses as :status]
       [belongs-to :types as :type]
       [belongs-to :users as :user]
       [has-many :comments]
       [habtm :tags via :post_tags]]
      [:comments
       [belongs-to :posts as :post]
       [belongs-to :statuses as :status]]
      [:categories [has-many :posts]]
      [:tags [habtm :posts via :post_tags]]
      [:types [has-many :posts]]
      [:statuses [has-many :posts]]))

(oyako/def-helper with-db #'schema)

(defn statuses []
  (with-db (oyako/fetch-all :statuses)))

(defn status [title]
  (with-db (oyako/fetch-one :statuses where ["title = ?" title])))

(defn public-only []  ["status_id = ?" (:id (status "Public"))])

(defn posts [& {:keys [include-hidden?]}]
  (let [public (public-only)]
   (with-db
     (oyako/fetch-all :posts
                      includes [:tags :category :comments :status :type :user]
                      where (when-not include-hidden?
                              {:posts public
                               :comments public})
                      :order "date_created desc"))))

(defn post [x & {:keys [include-hidden?]}]
  (let [clause (if (string? x) "url = ?" "id = ?")
        clause (if-not include-hidden?
                 (str clause " and status_id = ?")
                 clause)
        public (public-only)
        public_id (second public)]
   (with-db
     (oyako/fetch-one :posts
                      includes [:tags :category {:comments :status} :status]
                      where {:posts (if-not include-hidden?
                                      [clause [x public_id]]
                                      [clause x])
                             :comments (when-not include-hidden?
                                         public)}
                      order {:comments "date_created asc"}
                      limit 1))))

(defn comments [& {:keys [include-hidden?]}]
  (with-db
    (oyako/fetch-all :comments
                     includes [:post :status]
                     where (when-not include-hidden?
                             (public-only))
                     order :date_created)))

(defn comment [x]
  (with-db
    (oyako/fetch-one :comments
                     includes [:post :status]
                     where (if (string? x)
                             ["url = ?" x]
                             ["id = ?" x])
                     limit 1)))

(defn gravatar [comment]
  (gravatar/gravatar (or (or (:email comment)
                             (:ip comment)))))

(defn categories []
  (with-db
    (oyako/fetch-all :categories
                     includes :posts
                     order :title)))

(defn category [x & {:keys [include-hidden?]}]
  (with-db
    (oyako/fetch-one :categories
                     includes {:posts [:status :tags :category :comments]}
                     where {:categories (if (string? x)
                                          ["url = ?" x]
                                          ["id = ?" x])
                            :posts {:posts (when-not include-hidden?
                                             (public-only))
                                    :comments (when-not include-hidden?
                                                (public-only))}}
                     limit 1)))

(defn tags []
  (with-db
    (oyako/fetch-all :tags
                     includes :posts
                     order :title)))

(defn tag [x & {:keys [include-hidden?]}]
  (with-db
    (oyako/fetch-one :tags
                     includes {:posts [:status :tags :category :comments]}
                     where {:tags (if (string? x)
                                    ["url = ?" x]
                                    ["id = ?" x])
                            :posts {:posts (when-not include-hidden?
                                             (public-only))
                                    :comments (when-not include-hidden?
                                                (public-only))}})))

(defn post_tags [post_id tag_id]
  (with-db
    (oyako/fetch-all :post_tags
                     where ["post_id = ? and tag_id = ?" [post_id tag_id]])))

(defn users []
  (with-db (oyako/fetch-all :users)))

(defn types []
  (with-db (oyako/fetch-all :types)))

(defn user [username password]
  (first
   (filter
    #(and (= username (:username %))
          (= (:password %)
             (sha-256 (str password (:salt %)))))
    (users))))

(defn bare
  "Returns an object from table without any `includes`, suitable
  for editing and DB-updating."
  [table id]
  (with-db
    (oyako/fetch-one table
                     where ["id = ?" id]
                     limit 1)))

(defn tag-from-title [title]
  (let [s (map #(if (re-matches config/TAG-CATEGORY-REGEX (str %))
                  (str %) "-")
               (seq title))
        url (s/lower-case
             (s/replace-re #"\s+" "-" (apply str s)))]
    (in-table :tags {:title title :url url})))

(def run-hooks nil)
(defmulti run-hooks (fn [x] (table-meta x)))
(defmethod run-hooks :default [x] x)

(defmethod run-hooks :posts [post]
  (assoc post :html (markdown/markdown-to-html (:markdown post) false)))

(defmethod run-hooks :comments [c]
  (assoc c
    :html (markdown/markdown-to-html (:markdown c) true)))

(defn- where-id [x]
  ["id = ?" (:id x)])

(defn insert [x & {:keys [run-hooks?] :or {run-hooks? true}}]
  (let [x (if run-hooks? (run-hooks x) x)]
   (with-table [table x]
     (sql/insert-records table x))))

(defn insert-or-select [x where]
  (with-table [table x]
   (or (oyako/fetch-one table :where where)
       (do (insert (run-hooks x))
           (oyako/fetch-one table :where where)))))

(defn update [x]
  (with-table [table x]
    (when-not (bare table (:id x))
      (util/die "Can't update: record not found"))
    (sql/update-values table (where-id x) (run-hooks x))))

(defn delete [x]
  (with-table [table x]
    (sql/delete-rows table (where-id x))))

(defn create-user [username password]
  (let [salt (sha-256 (str (java.util.UUID/randomUUID)))
        password (sha-256 (str password salt))]
    (insert (in-table :users
                      {:username username
                       :password password
                       :salt salt}))
    ))

(defn add-tags-to-post [post tag-titles] 
  (doseq [tag-title tag-titles
          :let [wanted-tag (or (oyako/fetch-one :tags where ["title = ?" tag-title])
                               (tag-from-title tag-title))
                tag (insert-or-select wanted-tag
                                      ["url = ?" (:url wanted-tag)])]]
    (insert-or-select (in-table :post_tags
                                {:post_id (:id post)
                                 :tag_id (:id tag)})
                      ["post_id = ? and tag_id = ?" [(:id post) (:id tag)]])))

(defn remove-tags-from-post [post tag-urls]
  (doseq [tagname tag-urls
          :let [tag (tag tagname)]
          pt (post_tags (:id post) (:id tag))]
    (delete pt)))
