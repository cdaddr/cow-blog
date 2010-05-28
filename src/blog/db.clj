(ns blog.db
  (:require (clojure.contrib [sql :as sql])
            (net.briancarper [oyako :as oyako])
            (blog [config :as config]
                  [util :as util]
                  [time :as time]
                  [markdown :as markdown]))
  (:refer-clojure :exclude [comment]))

(defn table-meta [x]
  (:table (meta x)))

(defn in-table [table x]
  (with-meta x {:table table}))

(def schema
     (oyako/make-datamap
      [:posts
       [belongs-to :categories as :category]
       [belongs-to :statuses as :status]
       [belongs-to :types as :type]
       [has-many :comments]
       [habtm :tags via :post_tags]]
      [:comments
       [belongs-to :posts as :post]
       [belongs-to :statuses as :status]]
      [:categories [has-many :posts]]
      [:tags [habtm :posts via :post_tags]]
      [:types [has-many :posts]]
      [:statuses [has-many :posts]]))

(oyako/def-helper with-db #'config/DB #'schema)

(defn posts []
  (with-db
    (oyako/fetch-all :posts
                     includes [:tags :category :comments :status :type]
                     :order :date_created)))

(defn posts-with-tag [title]
  (filter #(some #{title} (map :url (:tags %)))
          (posts)))

(defn posts-with-category [title]
  (filter #(= title (-> % :category :url))
          (posts)))


(defn post [x]
  (with-db
    (oyako/fetch-one :posts
                     includes [:tags :category :comments]
                     where (if (string? x)
                             ["url = ?" x]
                             ["id = ?" x])
                     limit 1)))

(defn comments []
  (with-db
    (oyako/fetch-all :comments
                     includes [:post :status]
                     order :date_created)))

(defn comment [id]
  (with-db
    (oyako/fetch-one :comments
                     includes [:post :status]
                     where ["id = ?" id]
                     limit 1)))

(defn categories []
  (with-db
    (oyako/fetch-all :categories
                     includes :posts
                     order :title)))

(defn category [url]
  (with-db
    (oyako/fetch-one :categories
                     includes :posts
                     where ["url = ?" url]
                     limit 1)))

(defn tags []
  (with-db
    (oyako/fetch-all :tags
                     includes :posts
                     order :title)))

(defn tag [url]
  (with-db
    (oyako/fetch-one :tags
                     includes :posts
                     where ["url = ?" url])))

(defn bare
  "Returns an object from table without any `includes`, suitable
  for editing and DB-updating."
  [table id]
  (with-db
    (oyako/fetch-one table
                     where ["id = ?" id]
                     limit 1)))

(defmacro with-table [[table x] & body]
  `(with-db
     (let [x# ~x]
       (if-let [~table (:table (meta x#))]
         (do ~@body)
         (util/die "Can't determine the table for " x#)))))

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

(defn insert [x]
  (with-table [table x]
    (sql/insert-records table (run-hooks x))))

(defn update [x]
  (with-table [table x]
    (when-not (bare table (:id x))
      (util/die "Can't update: record not found"))
    (sql/update-values table (where-id x) (run-hooks x))))

(defn delete [x]
  (with-table [table x]
    (sql/delete-rows table (where-id x) x)))

(defn update [x]
  (with-table [table x]
    (sql/update-values table (where-id x) x)))

(defn safe-int [x]
  (when x (Integer/parseInt x)))
