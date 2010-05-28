(ns blog.server
  (:require (compojure [route :as route])
            (clojure [stacktrace :as trace])
            (clojure.contrib [string :as s])
            (blog [pages :as pages]
                  [layout :as layout]
                  [util :as util]
                  [config :as config])
            (ring.adapter [jetty :as jetty])
            (ring.util [response :as response])
            (ring.middleware cookies session flash)
            (hiccup [core :as hiccup]))
  (:use (compojure [core :only [defroutes GET POST ANY wrap!]])))

(defn- ip [request]
  (or (get-in request [:headers "x-forwarded-for"])
      (request :remote-addr)))

(def DEBUG true)

(defroutes blog-routes
  (GET "/" [paginate] (pages/index-page paginate))
  (GET ["/post/:title"] [title] (pages/post-page title))
  (GET ["/category/:title"] [title] (pages/category-page title))
  (GET ["/tag/:title"] [title] (pages/tag-page title))
  (GET "/set" [] {:body "Set session" :session {:foo :bar}})
  (GET "/get" {s :session} {:body (str "SESSION: " s)}))

(defroutes form-routes
  (POST "/post/:id" [id])
  (POST "/comment" {{:strs [post-id author email homepage markdown]} :form-params
                    :as request}
        (pages/do-add-comment post-id (ip request) author email homepage markdown)))

(defroutes static-routes
  (GET ["/:filename" :filename #".*"] [filename]
       (response/file-response filename {:root config/PUBLIC-DIR})))

(defroutes error-routes
  (ANY "*" [] {:status 404
               :body [:div [:h3 "404 - Page not found."]
                      [:p "You tried to access a page that doesn't exist.  One of us screwed up here.  Not pointing any fingers, but, well, it was probably you."]]}))

(defn catching-errors [handler]
  (let [newlines-to-br #(s/replace-re #"\n" "<br/>" %)
        pr-map #(vector :ul
                        (for [[k v] (sort %)]
                          [:li [:pre [:strong k] " " (hiccup/escape-html (pr-str v))]]))]
   (fn [request]
     (try (handler request)
          (catch Exception e
            {:status 500
             :body (if DEBUG
                     [:div [:h2 "ERROR!"]
                      [:h3 "Request:"]
                      [:pre
                       [:div (pr-map request)]]
                      [:h3 "Stacktrace"]
                      [:div [:pre
                             (newlines-to-br
                              (with-out-str
                                (trace/print-cause-trace e)))]]
                      ]
                     [:div [:h2 "Oops!"]
                      "The server just crashed.  <strong>WHAT DID YOU DO?!</strong>"])})))))

(defn wrap-flash [handler]
  (fn [request]
    (let [flash    (get-in request [:session :_flash])
          request  (assoc request :flash flash)]
      (when-let [response (handler request)]
        (if-let [flash (response :flash)]
          (assoc-in response [:session :_flash] flash)
          (assoc response :session
                 (dissoc (response :session) :_flash)))))))

(defn wrap-layout [handler]
  (fn [request]
    (when-let [response (handler request)]
      (let [new-body (layout/wrap-in-layout (:title response)
                                            (:body response)
                                            (get-in request [:flash :message])
                                            (get-in request [:flash :error]))]
        (assoc response
          :headers (merge (response :headers)
                          {"Content-Type" "text/html;charset=UTF-8"})
          :status (or (response :status) 200)
          :body new-body)))))

(defn wrap-flash-saver [handler]
  (fn [request]
    (when-let [response (handler request)]
      (let [status (response :status)]
        (if (or (nil? status) (= status 200))
          response
         (assoc response :flash (or (:flash response)
                                    (:flash request))))))))

(defroutes dynamic-routes
  blog-routes form-routes error-routes)

(wrap! dynamic-routes
       catching-errors
       wrap-layout
       wrap-flash-saver
       wrap-flash
       ring.middleware.session/wrap-session)

(defroutes all-routes
  static-routes dynamic-routes)

(defn start []
  (future
   (jetty/run-jetty (var all-routes) {:port 8080})))
