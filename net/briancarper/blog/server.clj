(ns net.briancarper.blog.server
  (:use (net.briancarper.blog html)
        (compojure.server jetty)
        (compojure.http servlet routes)))

(defmacro p
  "Makes the session, params, headers and request hash globally available (via thread-specific bindings)."
  [& rest]
  `(binding [net.briancarper.blog.html/*session* ~'session
             net.briancarper.blog.html/*params* ~'params
             net.briancarper.blog.html/*headers* ~'headers
             net.briancarper.blog.html/*request* ~'request]
     ~@rest))

(defservlet blog-servlet
  (GET "/" (p (index-page)))
  (GET "/blog/:parent/:child" (p (blog-page (route :parent) (route :child))))
  (GET "/blog/:page" (p (blog-page (route :page))))

  (GET "/page/*" (p (static-page (route :*))))

  (GET "/category/:name" (p (category-page (route :name))))
  (GET "/tag/:name" (p (tag-page (route :name))))

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

  (GET "/feed/category/:name" (category-rss (route :name)))
  (GET "/feed/tag/:name" (tag-rss (route :name)))
  (GET "/feed/comments/:name" (p (comment-rss (route :name))))

  (GET "/combined.css" (combined-css))
  (GET "/combined.js" (combined-js))

  (GET "/add" (p (new-post-page)))
  (POST "/add" (p (do-add-post)))
  (GET "/edit/:id" (p (edit-post-page (route :id))))
  (POST "/edit/:id" (p (do-edit-post (route :id))))
  (POST "/delete/:id" (p (do-remove-post (route :id))))
  (POST "/add-comment/:id" (p (do-add-comment (route :id))))
  (GET "/moderate-comments" (p (moderate-comments-page)))
  (GET "/edit-comment/:id" (p (edit-comment-page (route :id))))
  (POST "/edit-comment/:id" (p (do-edit-comment (route :id))))
  (POST "/remove-comment/:id" (p (do-remove-comment (route :id))))
  
  (GET "/*" (static-file (route :*)))
  (ANY "/*" (error-404)))

(defserver blog-server
  {:port 8080}
  "/*" blog-servlet)

(defn go []
  (start blog-server)
  (net.briancarper.blog.db/init-all))