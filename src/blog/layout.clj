(ns blog.layout
  "This namespace holds HTML-rendering code for the layout,
  and for minor bits of pages that had nowhere else to live."
  (:require (blog [config :as config]
                  [util :as util]
                  [db :as db]
                  [link :as link])
            (oyako [core :as oyako])
            (clojure.contrib [math :as math]
                             [string :as s])
            (sandbar [stateful-session :as session]))
  (:use (hiccup [core :only [html]]
                [page-helpers :only [link-to include-css include-js doctype]]
                [form-helpers :only [form-to submit-button label]])))

(def rss-icon
     (html
      [:span [:img.rss {:src "/img/rss.png" :alt "RSS"}] " "]))

(defn- nav [user]
  (let [link-with-count (fn [x]
                          (link-to (link/url x)
                                   (str (:title x) " (" (:num_posts x) ")")))]
   [:ul
    [:li
     (if user
       [:ul "Hello, " (:username user)
        [:li (link-to "/logout" "Log out")]
        [:li (link-to "/admin" "Control Panel")]]
       [:ul "Log in"
        [:li (link-to "/login" "Log in")]])]
    [:li 
     [:ul "Meta"
      [:li (link-to "/feed" "RSS")]]]
    [:li
     [:ul "Archives"
      [:li (link-to "/archives/date" "By date")]
      [:li (link-to "/archives/comments" "Most discussed")]
      [:li (link-to "/archives/tag-cloud" "Tag Cloud")]]]
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
                            :where ["num_posts > 0"]
                            :order "num_posts desc"))]]
    [:li
     [:ul "Tags"
      (map #(vector :li (link-with-count %))
           (oyako/fetch-all db/tags
                            :where ["num_posts > 0"]
                            :order "num_posts desc"))]]]))

(defn wrap-in-layout [title body user message error]
  (html
   (doctype :xhtml-strict)
   [:html
    [:head
     [:title config/SITE-TITLE (when title (str " - " title))]
     (include-css "/css/style.css")
     (include-js "/js/combined.js") ;;magic
     [:link {:type "application/rss+xml" :rel "alternate" :href "/feed"}]]
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
  ([xs count page-number query-params]
     (let [last-page-number (math/ceil (/ count
                                          config/POSTS-PER-PAGE))
           page-range (filter #(and (> % 0) (<= % last-page-number))
                              (range (- page-number 5)
                                     (+ page-number 5)))
           f (fn [p] (s/join "&" (concat [(str "?p=" p)]
                                         (map (fn [[k v]] (str (name k) "=" v))
                                              query-params))))]
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

(defn render-paginated [xs & {:keys [render-fn query-params count page-number]}]
  (list
   (map render-fn xs)
   (pagenav xs count page-number query-params)))

(defn status-span [x]
  (let [status (:status x)]
    [:span " [" [:span {:class status} status] "]"]))


