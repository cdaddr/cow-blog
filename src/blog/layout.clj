(ns blog.layout
  (:require (blog [config :as config]
                  [util :as util]
                  [db :as db]
                  [link :as link])
            (oyako [core :as oyako])
            (clojure.contrib [math :as math]
                             [string :as s])
            (sandbar [stateful-session :as session]))
  (:use (hiccup [core :only [html]]
                [page-helpers :only [link-to include-css include-js]]
                [form-helpers :only [form-to submit-button label]])))

(defn- nav [user]
  (let [link-with-count (fn [x]
                          (link-to (link/url x)
                                   (str (:title x) " (" (:num_posts x) ")")))]
   [:ul
    [:li
     (if user
       [:ul "Hello, " (:username user)
        [:li (link-to "/admin" "Control Panel")]
        [:li (link-to "/logout" "Log out")]]
       [:ul "Log in"
        [:li (link-to "/login" "Log in")]])]
    [:li 
     [:ul "Meta"
      [:li (link-to "/feed" "RSS")]]]
    [:li
     [:ul "Pages"
      (map #(vector :li (link/link %))
           (oyako/fetch-all :posts
                            :columns [:id :title :url :type :status]
                            :admin? user
                            :post-type "toplevel"
                            :order "title"))]]
    [:li
     [:ul "Categories"
      (map #(vector :li (link-with-count %))
           (oyako/fetch-all db/categories
                            :where ["num_posts > 0"]))]]
    [:li
     [:ul "Tags"
      (map #(vector :li (link-with-count %))
           (oyako/fetch-all db/tags
                            :where ["num_posts > 0"]
                            :order "num_posts desc"))]]]))

(defn wrap-in-layout [title body user message error]
  (html
   [:html
    [:head
     [:title config/SITE-TITLE (when title (str " - " title))]
     (include-css "/css/style.css")
     (include-js "/js/combined.js")] ;; magic
    [:body
     [:div#wrapper
      (when message [:div.message message])
      (when error [:div.error error])
      [:div#header
       [:h1 (link-to config/SITE-URL config/SITE-TITLE)]
       [:div.desc (link-to config/SITE-URL config/SITE-DESCRIPTION)]]
      [:div#sidebar (nav user)]
      [:div#content body]
      [:div#footer
       [:div
        "Powered by "
        (link-to "http://clojure.org" "Clojure") " and "
        (link-to "http://github.com/weavejester/compojure" "Compojure") " and "
        (link-to "http://briancarper.net" "Cows") "; "
        "theme based on " (link-to "http://shaheeilyas.com/" "Barecity")"."]]]]]))

(defn preview-div []
  [:div
   [:h4 "Preview"]
   [:div#preview]])

(defn form-row [lab name el]
  [:div (label name (str lab ":"))
   [:div.form (el name)]
   [:div.clear]])

(defn submit-row [lab]
  [:div.submit
   (submit-button lab)])

(defn pagenav
  ([xs num-xs page-number] (pagenav xs num-xs page-number (fn [p] (str "?p=" p))))
  ([xs num-xs page-number f]
     (let [last-page-number (math/ceil (/ num-xs
                                          config/POSTS-PER-PAGE))
           page-range (filter #(and (> % 0) (<= % last-page-number))
                              (range (- page-number 5)
                                     (+ page-number 5)))]
       [:div.pagenav
        [:span.navtext "Page " page-number " of " (if (zero? last-page-number) 1 last-page-number)]
        (if (> page-number 1)
          [:span.navnext
           (link-to (f 1) "&laquo; First")
           (link-to (f (dec page-number)) "&lt; Prev")])
        (for [p page-range]
          (if (= p page-number)
            [:span.num p]
            (link-to (f p) p)))
        (if (< page-number last-page-number)
          [:span.navnext
           (link-to (f (inc page-number)) "Next &raquo;")
           (link-to (f last-page-number) "Last &gt;")])])))

(defn paginate [xs page-number]
  (take config/POSTS-PER-PAGE
        (drop (* config/POSTS-PER-PAGE (dec page-number)) xs)))

(defn render-paginated [render-fn xs num-xs page-number & args]
  (list
   (map #(apply render-fn % args) xs)
   (pagenav xs num-xs page-number)))

(defn status-span [x]
  (let [status (:status x)]
    [:span " [" [:span {:class status} status] "]"]))


