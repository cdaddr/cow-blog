(ns net.briancarper.blog.db.test
  (:use (net.briancarper.blog db)))

(in-ns 'net.briancarper.blog.config)
(def db {:classname "com.mysql.jdbc.Driver"
              :subprotocol "mysql"
              :subname "//localhost/test_blog?user=test_blog&password=test_blog"})

(in-ns 'net.briancarper.blog.db)
(use '(clojure.contrib test-is sql))

(defn init-test-db []
  (let [id [:id :int "AUTO_INCREMENT" "NOT NULL" "PRIMARY KEY"]]
    (with-crud
     (with-db
      (apply do-commands
             (doall (map #(str "DROP TABLE IF EXISTS " %)
                         ["posts" "comments" "categories" "tags" "post_tags" "spam" "users"])))
      (init-db)
      (insert-records ::comments
                      {:id 123
                       :post_id 777
                       :author "cows"
                       :homepage "http://cows.com"
                       :markdown "hi"
                       :ip "0.0.0.0"
                       :approved 1
                       :html "<p>hi</p>"})
      (insert-records ::posts
                      {:id 555
                       :title "Foo"
                       :markdown "*foo bar*"
                       :html "<p><em>foo bar</em></p>"
                       :permalink "foo"
                       :type "blog"
                       :created "2009-01-01"
                       :category_id 1})
      (insert-records ::posts
                      {:id 777
                       :title "Bar"
                       :markdown "*bar baz*"
                       :html "<p><em>bar baz</em></p>"
                       :permalink "bar"
                       :type "page"
                       :created "2009-02-02"
                       :category_id 1})
      (insert-records ::categories
                      {:id 1
                       :name "Quux"
                       :permalink "quux"})
      (insert-records ::tags
                      {:id 888
                       :name "Blarg"
                       :permalink "blarg"})
      (insert-records ::post_tags
                      {:post_id 555
                       :tag_id 888})))))

(deftest test-normalize-tagname
  (is (= (normalize-tagname "foo") "foo"))
  (is (= (normalize-tagname "foo bar") "foo-bar")))

(deftest test-normalize-homepage
  (is (= (normalize-homepage "") nil))
  (is (= (normalize-homepage nil) nil))
  (is (= (normalize-homepage "http://foo.com") "http://foo.com"))
  (is (= (normalize-homepage "foo.com") "http://foo.com")))

(defmacro t [& rest]
  `(with-crud
    (with-db
     (init-test-db)
     (with-out-str (init-all))
     ~@rest)))

(deftest test-all-posts
  (t
   (is (= (count (all-posts)) 2))
   (is (= (:id (first (all-posts))) 777))))

(deftest test-post-init
  (t
   (let [post (get-post 777)]
     (is (= (count (:comments post)) 1))
     (is (= (count (:tags post)) 0))
     (is (= (:url post) "/page/bar")))
   (let [post (get-post 555)]
     (is (= (count (:comments post)) 0))
     (is (= (count (:tags post)) 1))
     (is (= (:url post) "/blog/foo")))))

(deftest test-post-delete
 (t
  (is (= (count (all-comments)) 1))
  (remove-post (get-post 555))
  (is (= (count (all-comments)) 1))
  (remove-post (get-post 777))
  (is (= (count (all-comments)) 0)))

 (t
  (is (= (count (all-tags)) 1))
  (remove-post (get-post 555))
  (is (= (count (all-tags)) 0))))

(deftest test-comment-delete
  (t
   (is (= (count (all-comments)) 1))
   (is (= (count (:comments (get-post 777))) 1))
   (remove-comment (get-comment 123))
   (is (= (count (all-comments)) 0))
   (is (= (count (:comments (get-post 777))) 0))))

(deftest test-tag-delete-from-post
  (t
   (is (= (count (:tags (get-post 555))) 1))
   (remove-tag-from-post (get-post 555) (get-tag 888))
   (is (= (count (all-tags)) 0))
   (is (= (count (:tags (get-post 555))) 0))))

(deftest test-get-or-add-tag
  (t
   (is (= (:id (get-or-add-tag "blarg")) 888))
   (is (= (:name (get-or-add-tag "FooBar")) "FooBar"))))

(deftest test-add-tag-to-post
  (t
   (is (= (count (:tags (get-post 555))) 1))
   (is (add-tag-to-post (get-post 555) "blarg"))
   (is (= (count (:tags (get-post 555))) 1))
   (is (add-tag-to-post (get-post 555) "foobar"))
   (is (= (count (:tags (get-post 555))) 2))))

(deftest test-sync-tags
  (t
   (sync-tags (get-post 555) ["a" "b" "c"])
   (is (= (count (:tags (get-post 555))) 3))
   (is (nil? (get-tag 888))))
  
  (t
   (add-tag-to-post (get-post 777) "blarg")
   (is (= (count (all-posts-with-tag "blarg")) 2))
   (is (= (count (all-tags)) 1))
   (sync-tags (get-post 555) ["a" "b" "c"])
   (is (= (count (all-posts-with-tag "blarg")) 1))
   (is (= (count (all-tags)) 4))))

(deftest test-edit-post
  (t
   (is (= (:title (get-post 555)) "Foo"))
   (is (nil? (:edited (get-post 555))))
   (edit-post (assoc (get-post 555) :title "ASDF"))
   (is (= (:title (get-post 555)) "ASDF"))
   (is (not (nil? (:edited (get-post 555)))))))

(deftest test-edit-comment
  (t
   (is (= (:author (first (:comments (get-post 777))))
          "cows"))   
   (edit-comment (assoc (get-comment 123) :author "BLARG"))
   (is (= (:author (get-comment 123)) "BLARG"))
   (is (= (:author (first (:comments (get-post 777))))
          "BLARG"))))

(deftest test-urls
  (t
   (is (= (:url (get-post "foo")) "/blog/foo"))
   (is (= (:url (get-post "bar")) "/page/bar"))
   (is (= (:url (get-category "quux")) "/category/quux"))
   (is (= (:url (get-tag "blarg")) "/tag/blarg"))))

(deftest test-tags-with-counts
  (t
   (is (= (count (all-tags-with-counts)) 1))
   (is (= (all-tags-with-counts) {(get-tag 888) 1}))))