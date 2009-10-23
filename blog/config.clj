(ns blog.config)

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

;; Change this.
(def PASSWORD-SALT "K@#$J@$#(FJ@#!$M@#n1NELKDwdjf9wef123krJ@!FKnjef2i#JR@R")
