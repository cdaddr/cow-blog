(ns blog.db
  (:require (clojure.contrib [sql :as sql])
            (net.briancarper [oyako :as oyako])
            (blog [config :as config]
                  [util :as util])))

(use '(clojure.pprint))
(defmacro p [x] `(pprint (macroexpand ~x)))

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
                     includes [:tags :category :comments :status :type])))


(defn post [id]
  (with-db
    (oyako/fetch-one :posts
                     includes [:tags :category :comments]
                     where ["id = ?" id]
                     limit 1)))

(defn comments []
  (with-db
    (oyako/fetch-all :comments
                     includes [:post :statuses]
                     order :date_created)))

(defn categories []
  (with-db
    (oyako/fetch-all :categories
                     includes :post
                     order :name)))

(defn tags []
  (with-db
    (oyako/fetch-all :tags
                     includes :posts
                     order :name)))

(defn tag [id]
  (with-db
    (oyako/fetch-one :tags
                     includes :posts
                     where ["id = ?" id])))

(defmacro with-table [[table x] & body]
  `(with-db
     (let [x# ~x]
       (if-let [~table (:table (meta x#))]
         (do ~@body)
         (util/die "Can't determine the table for " x#)))))

(defn- where-id [x]
  ["where id = ?" (:id x)])

(defn- save [x]
  (with-table [table x]
    (sql/update-values table (where-id x) x)))

(defn- delete [x]
  (with-table [table x]
    (sql/delete-rows table (where-id x) x)))

(defn- update [x]
  (with-table [table x]
    (sql/update-values table (where-id x) x)))
