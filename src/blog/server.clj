(ns blog.server
  (:require (compojure [route :as route])
            (clojure [stacktrace :as trace])
            (clojure.contrib [string :as s])
            (blog [pages :as pages]
                  [layout :as layout]
                  [util :as util])
            (ring.adapter [jetty :as jetty])
            (ring.util [response :as response])
            (ring.middleware cookies session))
  (:use (compojure [core :only [defroutes GET POST ANY wrap!]])))

(defroutes blog-routes
  (GET "/" [paginate] (pages/index-page paginate))
  (GET ["/post/:title", :title #"[^/.]+"] [title] (pages/post-page title)))
 
(defroutes static-routes
  (GET ["/:filename" :filename #".*"] [filename]
       (response/file-response filename {:root "public"}))
  (ANY "*" [] "ERROR!"))

(defn- newlines-to-br [str]
  (s/replace-re #"\n" "<br/>" str))

(defn catching-errors [handler]
  (fn [request]
    (try (when-let [response (handler request)]
           response)
         (catch Exception e
           {:status 500
            :body [:div [:div "ERROR!"]
                   [:div (newlines-to-br
                          (with-out-str
                            (trace/print-cause-trace e)))]
                   [:div request]]}))))

(defn wrap-layout [handler]
  (fn [request]
    (when-let [response (handler request)]
      (let [new-body (layout/wrap-in-layout
                      (select-keys response [:title :session :body]))]
        {:headers (merge (response :headers)
                        {"Content-Type" "text/html;charset=UTF-8"})
         :status (or (response :status) 200)
         :body new-body}))))

(wrap! blog-routes
       catching-errors
       ring.middleware.session/wrap-session
       wrap-layout)



(defroutes all-routes
  blog-routes static-routes)


(defn start []
  (future
   (jetty/run-jetty (var all-routes) {:port 8080})))
