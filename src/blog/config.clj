(ns blog.config
  (:require (net.briancarper [postgres-pool :as pg])))

(def SITE-TITLE "A Clojure Blog (\u03bb)")
(def SITE-URL "http://localhost:8080")
(def SITE-DESCRIPTION "Some guy's blog about Clojure.")

(def PUBLIC-DIR "public")  ;;CS/JS/images live here.  Relative path.

;; For now, only a single admin user can exist, and this is where the login details live.
;; use blog.admin/generate-user to generate a new user and then put it here.
;; Below:
;;   username = foo
;;   password = bar
(def ADMIN-USER {:username "foo"
                 :password "4bdec02a2dd5e6b6e28935bccf9bf4e7e5becce96b7845bee692768f4e4a810"})

(def TIME-ZONE "Canada/Pacific")
(def TIME-FORMAT "MMMM dd, yyyy @ h:mm a z")

;; Change this.
(def PASSWORD-SALT "K@#$J@$#(FJ@#!$M@#n1NELKDwdjf9wef123krJ@!FKnjef2i#JR@R")

(def DEFAULT-COMMENT-AUTHOR "Anonymous Cow")

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
