(ns blog.server
  (:require (compojure [route :as route])
            (blog [middleware :as middleware]
                  [pages :as pages]
                  [layout :as layout]
                  [util :as util]
                  [config :as config]
                  [error :as error]
                  [admin :as admin])
            (ring.adapter [jetty :as jetty])
            (ring.util [response :as response])
            (sandbar [stateful-session :as session])
            (ring.middleware cookies session flash file-info params))
  (:use (compojure [core :only [defroutes GET POST ANY wrap!]])))

(defn- ip [request]
  (or (get-in request [:headers "x-forwarded-for"])
      (request :remote-addr)))

(defn- strs [xs] (map name xs))
(defn user [] (session/session-get :user))

(defroutes blog-routes
  (GET "/" []                        (pages/index-page :page-number layout/PAGE-NUMBER))
  (GET ["/post/:title"] [title]      (pages/post-page title))
  (GET ["/category/:title"] [title]  (pages/category-page title :page-number layout/PAGE-NUMBER))
  (GET ["/tag/:title"] [title]       (pages/tag-page title :page-number layout/PAGE-NUMBER))
  (GET "/login" []                   (admin/login-page))
  (GET "/logout" []                  (admin/do-logout)))

(defroutes form-routes
  (POST "/post/:id" [id])
  (POST "/comment" {{:strs [post-id author email homepage markdown test]} :form-params
                    {referer "referer"} :headers
                    :as request}
        (pages/do-add-comment post-id (ip request)
                              author email homepage
                              markdown referer test))
  (POST ["/login"] {{:strs [username password]} :form-params}
        (admin/do-login username password)))

(defroutes admin-routes
  (GET "/admin" [] (admin/admin-page))
  
  (GET "/admin/add-post" [] (admin/add-post-page))
  
  (POST "/admin/add-post" {form-params :form-params}
        (apply admin/do-add-post (user)
               (map form-params (strs '[title url status_id
                                        type_id category_id
                                        tags markdown]))))

  (GET "/admin/edit-posts" []      (admin/edit-posts-page :page-number layout/PAGE-NUMBER))
  (GET "/admin/edit-post/:id" [id] (admin/edit-post-page id))
  (POST "/admin/edit-post" {form-params :form-params}
        (apply admin/do-edit-post (user)
               (map form-params (strs '[id title url
                                        status_id type_id category_id
                                        tags markdown "removetags[]"]))))

  (GET "/admin/edit-comments" [] (admin/edit-comments-page :page-number layout/PAGE-NUMBER))
  #_(GET "/admin/edit-comment/:id" [id] (admin/edit-comment-page id))
  #_(POST "/admin/edit-comment" {form-params :form-params}
          (apply admin/do-edit-comment
                 (map form-params (strs '[id post_id status_id
                                          author email homepage
                                          ip markdown]))))

  ;; Posts and categories are so similar they can share edit forms
  (GET ["/admin/edit-:which" :which #"tags|categories"] [which]
       (admin/edit-tags-categories-page (keyword which) :page-number layout/PAGE-NUMBER))
  (GET ["/admin/edit-:which/:id" :which #"tag|category"] [which id]
       (admin/edit-tag-category-page (keyword which) id))
  (POST ["/admin/edit-:which" :which #"tag|category"] {{:strs [xid title url]} :form-params
                                                       {which "which"} :route-params}
        (admin/do-edit-tag-category (keyword which) xid title url))
  (POST ["/admin/add-:which" :which #"tag|category"] {{:strs [title url]} :form-params
                                                      {which "which"} :route-params}
        (admin/do-add-tag-category (keyword which) title url))

  (POST ["/admin/delete-:which" :which #"tag|category"] {{:strs [xid]} :form-params
                                                         {which "which"} :route-params}
        (admin/do-delete-tag-category (keyword which) xid))

  ;; Catch all routes under /admin
  (GET "/admin/*" []))

(defroutes static-routes
  (GET "/js/combined.js" [] (pages/combined-js))
  (GET ["/:filename" :filename #".*"] [filename]
       (response/file-response filename {:root config/PUBLIC-DIR})))

(defroutes error-routes
  (ANY "*" [] (error/error 404 "Page not found."
                           "You tried to access a page that doesn't exist.  One of us screwed up here.
                            Not pointing any fingers, but, well, it was probably you.")))

(wrap! admin-routes middleware/wrap-admin)

(defroutes dynamic-routes
  blog-routes form-routes admin-routes error-routes)

(wrap! dynamic-routes
       middleware/wrap-page-number
       middleware/wrap-layout
       session/wrap-stateful-session
       middleware/catching-errors
       ring.middleware.params/wrap-params
       )

(wrap! static-routes
       middleware/wrap-expires-header
       ring.middleware.file-info/wrap-file-info)

(defroutes all-routes
  static-routes dynamic-routes)

(defn start
  "Start Jetty in a background thread.  Note there's currently no
  way to stop Jetty once you start it."
  []
  (future
   (jetty/run-jetty (var all-routes) {:port 8080})))
