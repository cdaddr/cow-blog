(ns net.briancarper.blog.server
  (:use compojure
        (net.briancarper.blog [global :as global] html)))

(defmacro p
  "Makes the session, params, headers and request hash globally available (via thread-specific bindings)."
  [& rest]
  `(binding [global/*session* ~'session
             global/*params* ~'params
             global/*request* ~'request]
     ~@rest))

(defroutes blog
  (GET "/" (p (index-page)))
  (GET "/blog/:parent/:child" (p (blog-page (params :parent) (params :child))))
  (GET "/blog/:page" (p (blog-page (params :page))))

  (GET "/page/*" (p (static-page (params :*))))

  (GET "/category/:name" (p (category-page (params :name))))
  (GET "/tag/:name" (p (tag-page (params :name))))

  (GET "/archives" (p (archives-page)))
  (GET "/archives-all" (p (archives-all)))

  (GET "/login" (p (login-page)))
  (POST "/login" (p (do-login params)))
  (GET "/logout" (p (do-logout)))

  (GET "/search" (p (search-results)))

  ;; These are for backwards compatibility with Wordpress
  (GET "/feed" (p (rss-index)))
  (GET "/feed/" (p (rss-index)))
  (GET "/feed/atom" (p (rss-index)))
  (GET "/feed/atom/" (p (rss-index)))

  (GET "/feed/category/:name" (category-rss (params :name)))
  (GET "/feed/tag/:name" (tag-rss (params :name)))
  (GET "/feed/comments/:name" (p (comment-rss (params :name))))

  (GET "/combined.css" (combined-css))
  (GET "/combined.js" (combined-js))

  (GET "/add" (p (new-post-page)))
  (POST "/add" (p (do-add-post)))
  (GET "/edit/:id" (p (edit-post-page (params :id))))
  (POST "/edit/:id" (p (do-edit-post (params :id))))
  (POST "/delete/:id" (p (do-remove-post (params :id))))
  (POST "/add-comment/:id" (p (do-add-comment (params :id))))
  (GET "/moderate-comments" (p (moderate-comments-page)))
  (GET "/edit-comment/:id" (p (edit-comment-page (params :id))))
  (POST "/edit-comment/:id" (p (do-edit-comment (params :id))))
  (POST "/remove-comment/:id" (p (do-remove-comment (params :id))))
  
  (GET "/*" (static-file (params :*)))
  (ANY "/*" (error-404)))

(defserver blog-server
  {:port 8080}
  "/*" (servlet blog))

(defn go []
  (start blog-server)
  (net.briancarper.blog.db/init-all))