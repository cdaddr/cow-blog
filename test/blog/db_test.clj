(ns blog.db-test)

(in-ns 'blog.db)
(use '(clojure test))
(require '(net.briancarper [postgres-pool :as pool]))
(require '(blog.db [postgres :as p]))

(def test-db (pool/postgres-pool {:database "blogtest"
                                  :username "blogtest"
                                  :password "blogtest"}))

(def test-data
     {:tags
      [{:id 1 :name "Tag1" :url "tag1"}
       {:id 2 :name "Tag2" :url "tag2"}]
      :posts
      [{:id 1 :title "foo" :url "foo" :author "Brian"
        :markdown "This is some text" :html "This is some text"
        :status_id 1 :type_id 1}]
      :comments
      [{:id 1 :post_id 1 :status_id 1 :author "Someone" :markdown "Blah" :html "Blah"}]
      :post_tags
      [{:post_id 1, :tag_id 1}
       {:post_id 1 :tag_id 2}]})

(defmacro with-test-db [& body]
  `(binding [config/DB test-db]
     (with-db
       ~@body)))

(defn init-test-db [f]
  (with-test-db (p/init-db-postgres))
  (f))

(defn seed-test-data [f]
  (with-test-db
    (sql/do-commands "truncate table post, tag, post_tag cascade")
    (doseq [[table data] test-data]
      (apply sql/insert-records table data)))
  (f))

(use-fixtures :once init-test-db seed-test-data)

(deftest test-test-db
  (with-test-db
    (is true)))

(deftest test-posts
  (with-test-db
    (is (= (map :id (posts))
           (map :id (:posts test-data))))

    (is (= 1 (:id (post 1))))))

