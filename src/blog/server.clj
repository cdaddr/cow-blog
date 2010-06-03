(ns blog.server
  (:require (compojure [route :as route])
            (blog [middleware :as middleware]
                  [pages :as pages]
                  [layout :as layout]
                  [util :as util]
                  [config :as config]
                  [error :as error])
            (ring.adapter [jetty :as jetty])
            (ring.util [response :as response])
            (ring.middleware cookies session flash file-info))
  (:use (compojure [core :only [defroutes GET POST ANY wrap!]])))

(defn- ip [request]
  (or (get-in request [:headers "x-forwarded-for"])
      (request :remote-addr)))

(defroutes blog-routes
  (GET "/" {{page-number "p"} :query-params} (pages/index-page page-number))
  (GET ["/post/:title"] [title] (pages/post-page title))
  (GET ["/category/:title"] [title] (pages/category-page title))
  (GET ["/tag/:title"] [title] (pages/tag-page title)))

(defroutes form-routes
  (POST "/post/:id" [id])
  (POST "/comment" {{:strs [post-id author email homepage markdown]} :form-params
                    {referer "referer"} :headers
                    :as request}
        (pages/do-add-comment post-id (ip request) author email homepage markdown referer)))

(defroutes static-routes
  (GET "/js/combined.js" [] (pages/combined-js))
  (GET ["/:filename" :filename #".*"] [filename]
       (response/file-response filename {:root config/PUBLIC-DIR})))

(defroutes error-routes
  (ANY "*" [] (error/error 404 "Page not found."
                           "You tried to access a page that doesn't exist.  One of us screwed up here.  Not pointing any fingers, but, well, it was probably you.")))

(defroutes dynamic-routes
  blog-routes form-routes error-routes)

(wrap! dynamic-routes
       middleware/catching-errors
       middleware/wrap-layout
       middleware/wrap-flash-saver
       middleware/wrap-flash
       ring.middleware.session/wrap-session)

(wrap! static-routes
       ring.middleware.file-info/wrap-file-info)

(defroutes all-routes
  static-routes dynamic-routes)

(defn start
  "Start Jetty in a background thread.  Note there's currently no
  way to stop Jetty once you start it."
  []
  (future
   (jetty/run-jetty (var all-routes) {:port 8080})))
