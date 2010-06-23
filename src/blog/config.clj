(ns blog.config
  (:require (net.briancarper [postgres-pool :as pg])))

(def DEBUG true)

(def SITE-TITLE "A Clojure Blog (\u03bb)")
(def SITE-URL "http://localhost:8080")
(def SITE-DESCRIPTION "Some guy's blog about Clojure.")

(def PUBLIC-DIR "public")  ;;CS/JS/images live here.  Relative path.
(def TIME-ZONE "Canada/Pacific")
(def TIME-FORMAT "MMMM dd, yyyy @ h:mm a z")

;; Change this.
(def PASSWORD-SALT "K@#$J@$#(FJ@#!$M@#n1NELKDwdjf9wef123krJ@!FKnjef2i#JR@R")

(def DEFAULT-COMMENT-AUTHOR "Anonymous Cow")

(def POSTS-PER-PAGE 10)
(defn page-offset [page-number]
  (* (dec page-number) POSTS-PER-PAGE))

(def TAG-CATEGORY-TITLE-REGEX #"^[-A-Za-z0-9_. ]+$")
(def TAG-CATEGORY-URL-REGEX #"^[a-z0-9_-]+$")

(def CAPTCHA #"(?i)^\s*moo\s*$")

(def DB nil)

(def DB (pg/postgres-pool {:database "blogtest"
                           :username "blogtest"
                           :password "blogtest"}))

;; Pick a DB...
(comment
  ;;Postgres pool, uses net.briancarper.postgres-pool from Clojars
  (def DB (pg/postgres-pool {:database "blogtest"
                             :username "blogtest"
                             :password "blogtest"}))

  ;; Normal single-connection postgres
  (def DB {:classname "org.postgresql.Driver"
           :subprotocol "postgresql"
           :subname "//localhost/blogtest"
           :username "blogtest"
           :password "blogtest"})

  ;; MySQL
  (def DB {:classname "com.mysql.jdbc.Driver"
           :subprotocol "mysql"
           :subname "//localhost/origami?user=blogtest&password=blogtest"}))
