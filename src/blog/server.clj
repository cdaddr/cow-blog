(ns blog.server
  (:use (compojure control)
        (compojure.server jetty)
        (compojure.http servlet
                        routes
                        helpers
                        session
                        request
                        middleware)
        (blog util layout pages admin rss)))

(defn- admin [session]
  (:username session))

(defn- ip [request]
  (or ((:headers request) "x-forwarded-for")
      (:remote-addr request)))

(defroutes blog-routes
  (GET "/"                   (index-page (admin session)))
  (GET "/post/:id"           (post-page (admin session) (:id params)))
  (GET "/tag/:tag"           (tag-page (admin session) (:tag params)))
  (GET "/category/:category" (category-page (admin session) (:category params)))
  (GET "/admin/login"        (login-page)))

(defroutes form-routes
  (POST "/admin/login"          (do-login (:username params) (:password params)))
  (POST "/add-comment/:post-id" (:email params)))

(defroutes admin-routes
  (GET "/admin/add-post"      (add-post-page))
  (GET "/admin/edit-post/:id" (edit-post-page (:id params))))

(defroutes admin-form-routes
  (POST "/admin/logout"               (do-logout))
  (POST "/admin/do-add-post"          (apply do-add-post
                                             (ip request)
                                             (map params [:id :title :category :tags :markdown])))
  (POST "/admin/do-edit-post/:old-id" (apply do-edit-post
                                             (:old-id params)
                                             (ip request)
                                             (map params [:id :title :category :tags :markdown]))))

(defroutes static-routes
  (GET "/rss.xml"        (rss-index))
  (GET "/js/combined.js" (combined-js))
  (GET "/*"              (or (serve-file (:* params)) :next)))

(defroutes error-routes
  (ANY "/*" [404 "404 - Page not found"]))

;; This is to work around a bug in Compojure
;; (or a misunderstanding on my part about how Compojure should work)
;; This prevents NPEs and INTERNAL_SERVER_ERRORS due to with-mimetypes
;; barfing on requests with no :body (e.g. when file isn't found and
;; we want to fallthrough to error-routes
(defn short-circuit [handler]
  (fn [request]
    (let [response (handler request)]
      (when (:body response)
        response))))

(defn with-layout [handler]
  (fn [request]
    (when-let [response (handler request)]
      (assoc response
        :headers (merge (:headers response)
                        {"Content-Type" "text/html;charset=UTF-8"})
        :body (page
                  (admin (:session request))
                  (:title response)
                  (:flash request)
                  (:session request)
                (:body response))))))

(defn admin-only [handler]
  (fn [request]
    (when (admin (:session request))
      (handler request))))

(decorate blog-routes       with-layout with-session)
(decorate form-routes       with-session)
(decorate admin-routes      with-layout admin-only with-session)
(decorate admin-form-routes admin-only with-session)
(decorate static-routes     with-mimetypes short-circuit)  ; FIXME

(defroutes all-routes
  admin-routes
  admin-form-routes
  blog-routes
  form-routes
  static-routes
  error-routes)

(defserver blog-server
  {:port 8080 :host "localhost"}
  "/*" (servlet all-routes))
