(ns blog.middleware
  (:require (blog [config :as config]
                  [layout :as layout])
            (clojure [stacktrace :as trace])
            (clojure.contrib [string :as s])
            (hiccup [core :as hiccup])))

(defn catching-errors
  "Run a handler, and if there's an exception, show a short or long
  message (depending on value of config/DEBUG) in HTML form in the browser."
  [handler]
  (let [newlines-to-br #(s/replace-re #"\n" "<br/>" %)
        pr-map #(vector :ul
                        (for [[k v] (sort %)]
                          [:li [:strong k] " " (hiccup/escape-html (pr-str v))]))]
    (fn [request]
      (try (handler request)
           (catch Exception e
             {:status 500
              :body (if config/DEBUG
                      [:div [:h2 "ERROR!"]
                       [:h3 "Request:"]
                       [:pre [:div (pr-map request)]]
                       [:h3 "Stacktrace"]
                       [:div [:pre
                              (newlines-to-br
                               (with-out-str
                                 (trace/print-cause-trace e)))]]
                       ]
                      [:div [:h2 "Oops!"]
                       "The server just crashed.  <strong>WHAT DID YOU DO?!</strong>"])})))))

(defn wrap-layout
  "Run a handler, and wrap the result in some standard HTML
  to render the site layout/theme.  Also add Content-Type header
  and status code 200 if not specified."
  [handler]
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

(defn wrap-flash
  "Run handler in the context of a one-request lifetime session
  value ('flash')."
  [handler]
  (fn [request]
    (let [flash    (get-in request [:session :_flash])
          request  (assoc request :flash flash)]
      (when-let [response (handler request)]
        (if-let [flash (response :flash)]
          (assoc-in response [:session :_flash] flash)
          (assoc response :session
                 (dissoc (response :session) :_flash)))))))

(defn wrap-flash-saver
  "Given a flash from wrap-flash, if the status code of
  the response is anything but 200 or blank, preserve the flash.
  This is mainly to keep the flash from being eaten by redirects."
  [handler]
  (fn [request]
    (when-let [response (handler request)]
      (let [status (response :status)]
        (if (or (nil? status) (= status 200))
          response
          (assoc response :flash (or (:flash response)
                                     (:flash request))))))))
