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

(defroutes blog-routes
  (GET "/" []                        (pages/index-page :page-number layout/PAGE-NUMBER))
  (GET ["/post/:title"] [title]      (pages/post-page title))
  (GET ["/category/:title"] [title]  (pages/category-page title :page-number layout/PAGE-NUMBER))
  (GET ["/tag/:title"] [title]       (pages/tag-page title :page-number layout/PAGE-NUMBER))
  (GET "/login" []                   (admin/login-page))
  (GET "/logout" []                  (admin/do-logout))
  #_(GET "/session" [] {:body (pr-str (session/session-get :user)) #_(pr-str @session/*sandbar-session*)}))

(defroutes form-routes
  (POST "/post/:id" [id])
  (POST "/comment" {{:strs [post-id author email homepage markdown]} :form-params
                    {referer "referer"} :headers
                    :as request}
        (pages/do-add-comment post-id (ip request) author email homepage markdown referer))
  (POST ["/login"] {{:strs [username password]} :form-params}
        (admin/do-login username password))
  )

(defroutes admin-routes
  (GET "/admin" [] (admin/admin-page))
  
  (GET "/admin/add-post" [] (admin/add-post-page))
  
  (POST "/admin/add-post" {{:strs [title url status_id
                                   type_id category_id
                                   tags markdown]} :form-params}
        (admin/do-add-post (session/session-get :user)
                           title url status_id
                           type_id category_id
                           tags markdown))

  (GET "/admin/edit-posts" []      (admin/edit-posts-page :page-number layout/PAGE-NUMBER))
  (GET "/admin/edit-post/:id" [id] (admin/edit-post-page id))
  (POST "/admin/edit-post" {{removetags "removetags[]"
                             :strs [id title url status_id type_id category_id tags markdown]} :form-params}
        (admin/do-edit-post id (session/session-get :user)
                            title url status_id type_id category_id tags removetags markdown))
  (GET "/admin/*" [])
  )

(defroutes static-routes
  (GET "/js/combined.js" [] (pages/combined-js))
  (GET ["/:filename" :filename #".*"] [filename]
       (response/file-response filename {:root config/PUBLIC-DIR})))

(defroutes error-routes
  (ANY "*" [] (error/error 404 "Page not found."
                           "You tried to access a page that doesn't exist.  One of us screwed up here.  Not pointing any fingers, but, well, it was probably you.")))

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
