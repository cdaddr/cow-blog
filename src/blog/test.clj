(ns blog.test
  (:use (blog db util)))

(defn stress-test
  "Create a new dummy post called post-name, then simulate n users simultaneously trying to add comments to it.  Tests thread safety.  Values of n above 100 cause my computer to die."
  [post-name n]
  (remove-post post-name)
  (add-post {:id post-name :title post-name :markdown "foo" :category (make-category {:title "foo"})})
  (let [post (get-post post-name)]
   (dotimes [i n]
     (let [name (str "Foo" i)]
       (.start
        (Thread.
         (fn []
           (add-comment post
                        {:author name
                         :email "brian@briancarper.net"
                         :markdown (str "This is some *text* (" i ")")}))))))))

(defn double-stress-test
  "Run the stress test in parallel on two posts.  Tests thread safety."
  [post-name1 post-name2 n]
  (.start (Thread. #(stress-test post-name1 n)))
  (.start (Thread. #(stress-test post-name2 n))))
