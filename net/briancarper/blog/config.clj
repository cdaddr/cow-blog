;; Edit this file to reflect your setup.

(ns net.briancarper.blog.config)

(def db {:classname "com.mysql.jdbc.Driver"
         :subprotocol "mysql"
         :subname "//localhost/test_blog?user=test_blog&password=test_blog"
         :create true})

(def *posts-per-page* 10)
(def *site-name* "MY CLOJURE BLOG")
(def *site-url* "http://briancarper.net")
(def *site-description* "Some guy's blog about programming and Linux and cows")

;; Random garbage added to passwords.  Change this.
;; FIXME: This is lame.
(def *password-salt* "skadjfl;kasjf;dlkasdjf;lkafjasd")
