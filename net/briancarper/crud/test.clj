(ns net.briancarper.crud.test
  (:use [net.briancarper.crud :as crud]
        [clojure.contrib test-is sql]
        com.infolace.format))

;; If you want to run these tests, make an empty mysql database "crud_test", username "crud", password "crud".

(defonce test-data (ref {}))
(def test-db {:classname "com.mysql.jdbc.Driver"
              :subprotocol "mysql"
              :subname "//localhost/crud_test?user=crud&password=crud"})

(defmacro with-crud [& rest]
  `(binding [net.briancarper.crud/data test-data
             net.briancarper.crud/db test-db]
     ~@rest))

(defn init-test-db []
  (let [id [:id :int "AUTO_INCREMENT" "NOT NULL" "PRIMARY KEY"]]
    (with-crud
     (with-db
      (do-commands
       "DROP TABLE IF EXISTS posts;"
       "DROP TABLE IF EXISTS comments;")
      (create-table ::posts
                    id
                    [:title "VARCHAR(255)"]
                    [:content "TEXT"])
      (create-table ::comments
                    id
                    [:post_id :int "NOT NULL"]
                    [:content "TEXT"])
      (insert-records ::posts
                      {:id 1 :title "foo" :content "bar baz"}
                      {:id 7 :title "blarg" :content "cows"})
      (insert-records ::comments
                      {:id 123 :post_id 7 :content "hi"})))))


;; Hooks to test later
(defmethod fetch ::posts [table obj]
  (if (string? obj)
    (fetch ::posts {:title obj})
    'bad))

(defmethod after-init ::posts [post]
  (assoc post :comments (fetch-all ::comments {:post_id (:id post)})))

(defmethod after-delete ::posts [post]
  (dorun (map delete (fetch-all ::comments {:post_id (:id post)}))))

;; Helper - re-init the DB from scratch
(defmacro t [& rest]
  `(with-crud
    (init-test-db)
    (init ::posts)
    (init ::comments)
    ~@rest))

;; Away we go
(deftest test-init
  (t
   (is (= (sort (keys @test-data)) (sort '(::posts ::comments))))
   (is (= (count (keys @(@test-data ::posts))) 2))
   (is (= (class (first (keys @(@test-data ::posts)))) BigInteger))))

(deftest test-fetch
  (t
   (let [obj {:table ::posts :id 1 :title "foo" :content "bar baz" :comments ()}]
     (is (= obj (fetch ::posts 1)))
     (is (= obj (fetch ::posts {:title "foo" :content "bar baz"}))))))

(deftest test-fetch-all
  (t
   (is (= (count (fetch-all ::posts)) 2))
   (is (= (count (fetch-all ::comments)) 1))))

(deftest test-create
  (t
   (let [new-obj {:title "blarg" :content "blargfoo"}
         db-obj (create ::posts new-obj)
         refetched-obj (fetch ::posts (:id db-obj))]
     (is (= (:table new-obj) nil))
     (is (= (:table db-obj) ::posts))
     (is (= (:table refetched-obj) ::posts))
     
     (is (= (:id db-obj) (:id refetched-obj)))
     (is (= (:title new-obj) (:title db-obj)))
     (is (= (:title new-obj) (:title refetched-obj))))))

(deftest test-update
  (t
   (let [obj (assoc (fetch ::posts 1)
               :title "NEW TITLE")]
     (is (= (:title (fetch ::posts 1)) "foo"))
     (is (= (:content (fetch ::posts 1)) "bar baz"))
     (update obj)
     (is (= (:title (fetch ::posts 1)) "NEW TITLE"))
     (is (= (:content (fetch ::posts 1)) "bar baz"))))
  (t
   (let [obj (assoc (fetch ::posts 1)
               :title nil)]
     (update obj)
     (is (= (:title (fetch ::posts 1)) nil)))))

(deftest test-delete
  (t
   (let [obj (fetch ::posts 1)]
     (is (= (delete obj)) 1)
     (is (= (fetch ::posts 1) nil))))
  (t
   (is (= (delete {:table ::posts :id 12345}) 0))))

(deftest test-init
  (with-crud
   (is (= (init ::posts)) 2)))

(deftest test-fetch-hook
  (t
   (is (= (fetch ::posts "foo") (fetch ::posts 1)))))

(deftest test-init-hook
  (t
   (is (= (:comments (fetch ::posts "foo")) ()))))

(deftest test-delete-hooks
  (t
   (let [post (fetch ::posts 7)]
     (is (not (nil? (fetch ::comments 123))))
     (is (= (first (:comments post)) (fetch ::comments 123)))
     (delete post)
     (is (nil? (fetch ::posts 7)))
     (is (nil? (fetch ::comments 123))))))
