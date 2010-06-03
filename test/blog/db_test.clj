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
      [{:title "Tag1" :url "tag1"}
       {:title "Tag2" :url "tag2"}]
      :posts
      (concat
       [{:title "A Sample Post Title" :url "a-sample-post-title" :author "Brian"
         :markdown "Lorem ipsum dolor sit amet, consectetur adipisicing elit, sed do eiusmod tempor incididunt ut labore et dolore magna aliqua. Ut enim ad minim veniam, quis nostrud exercitation ullamco laboris nisi ut aliquip ex ea commodo consequat. Duis aute irure dolor in reprehenderit in voluptate velit esse cillum dolore eu fugiat nulla pariatur. Excepteur sint occaecat cupidatat non proident, sunt in culpa qui officia deserunt mollit anim id est laborum.

Sed ut perspiciatis unde omnis iste natus error sit voluptatem accusantium doloremque laudantium, totam rem aperiam, eaque ipsa quae ab illo inventore veritatis et quasi architecto beatae vitae dicta sunt explicabo. Nemo enim ipsam voluptatem quia voluptas sit aspernatur aut odit aut fugit, sed quia consequuntur magni dolores eos qui ratione voluptatem sequi nesciunt. Neque porro quisquam est, qui dolorem ipsum quia dolor sit amet, consectetur, adipisci velit, sed quia non numquam eius modi tempora incidunt ut labore et dolore magnam aliquam quaerat voluptatem. Ut enim ad minima veniam, quis nostrum exercitationem ullam corporis suscipit laboriosam, nisi ut aliquid ex ea commodi consequatur? Quis autem vel eum iure reprehenderit qui in ea voluptate velit esse quam nihil molestiae consequatur, vel illum qui dolorem eum fugiat quo voluptas nulla pariatur?"
         :html "<p>Lorem ipsum dolor sit amet, consectetur adipisicing elit, sed do eiusmod tempor incididunt ut labore et dolore magna aliqua. Ut enim ad minim veniam, quis nostrud exercitation ullamco laboris nisi ut aliquip ex ea commodo consequat. Duis aute irure dolor in reprehenderit in voluptate velit esse cillum dolore eu fugiat nulla pariatur. Excepteur sint occaecat cupidatat non proident, sunt in culpa qui officia deserunt mollit anim id est laborum.</p><p>Sed ut perspiciatis unde omnis iste natus error sit voluptatem accusantium doloremque laudantium, totam rem aperiam, eaque ipsa quae ab illo inventore veritatis et quasi architecto beatae vitae dicta sunt explicabo. Nemo enim ipsam voluptatem quia voluptas sit aspernatur aut odit aut fugit, sed quia consequuntur magni dolores eos qui ratione voluptatem sequi nesciunt. Neque porro quisquam est, qui dolorem ipsum quia dolor sit amet, consectetur, adipisci velit, sed quia non numquam eius modi tempora incidunt ut labore et dolore magnam aliquam quaerat voluptatem. Ut enim ad minima veniam, quis nostrum exercitationem ullam corporis suscipit laboriosam, nisi ut aliquid ex ea commodi consequatur? Quis autem vel eum iure reprehenderit qui in ea voluptate velit esse quam nihil molestiae consequatur, vel illum qui dolorem eum fugiat quo voluptas nulla pariatur?</p>"
         :status_id 1 :type_id 1 :date_created (time/str-to-dbdate :edit "2010-05-27 15:01:00 -07:00")}
        {:title "bar" :url "bar" :author "Brian"
         :markdown "This is some text.  *foo* __bar__ `baz`" :html "WRONG"
         :status_id 1 :type_id 1 :date_created (time/str-to-dbdate :edit "2010-05-26 12:23:00 -07:00")}]
       (for [x (range 100) :let [name (str "test" x)]]
         {:title name :url name :author "Brian"
          :markdown (str "Some text... " name) :html (str "<p>Some text... " name "</p>")
          :status_id 1 :type_id 1 :date_created (time/str-to-dbdate :edit "2010-05-27 14:00:00 -07:00")}))
      :comments
      [{:post_id 1 :status_id 1 :author "Someone" :markdown "Blah" :html "Blah" :ip "123.123.123.123"}]
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
    (is (= (set (map :id (posts)))
           (set (map :id (:posts test-data)))))

    (is (= 2 (:id (post "bar"))))))

