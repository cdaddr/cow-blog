(ns net.briancarper.blog.html.layout
  (:use compojure
        net.briancarper.blog.global)
  (:require (net.briancarper.blog [global :as global]
                                  [config :as config]
                                  [db :as db])))

(defn block
  "Wraps some forms in HTML for a 'block', which is a series of div that can be used to display a box around some content given a proper CSS setup.  If post is non-nil, the category of the post is used to style the block."
  [post & content]
  (let [cat (if post (:permalink (:category post)) "uncategorized")
        has-cat? (not (= cat "uncategorized"))]
    [:div {:class (str "block block-" cat)}
     [:div.block-top]
     [:div.block-content
      [:div (when has-cat?
              {:class cat :style (str "background: url(/img/" cat "-bg.png) no-repeat top right")})
       content]]
     [:div.block-bottom]]))


(defn navbar
  "Returns HTML for the navbar (floating side navigation area)."
  []
  [:div.navbar
   [:div.navbar-top]
   [:div#navigation.navbar-content
    [:div.home
     [:h1 (link-to "/" config/*site-name*)]]
    [:div.categories
     [:h3 "Blog"]
     [:ul
      (map #(vector :li (link-to (:url %) (:name %))) (db/all-display-categories))]
     [:h3 "Creations"]
     [:ul
      (map #(let [post (db/get-post %)]
              (vector :li (link-to (:url post) (:title post))))
           (db/all-toplevel-pages))]
     
     [:h3 "Meta"]
     [:ul
      [:li (link-to "/archives" "Archives")]
      [:li (link-to "/feed" "RSS")]]
     [:h3 "Cows"]
     [:ul
      [:li (link-to "/page/about" "About Me")]]
     [:h3 "Search"]
     [:div.search (form-to [GET "/search"]
                    (text-field :q) (submit-button "Moo"))]
     (if-logged-in
      (list
       [:h3 "You Are Logged In"]
       [:ul
        [:li (link-to "/add" "Add post")]
        [:li (link-to "/moderate-comments" "Moderate")]
        [:li (link-to "/logout" "Logout")]]))]]
   [:div.navbar-bottom]])


(defn footer
  "Returns HTML for the page footer."
  []
  [:div#footer
   "Powered by " (link-to "http://clojure.org" "Clojure")
   " and " (link-to "http://github.com/weavejester/compojure/tree/master" "Compojure")
   " and " (link-to "http://briancarper.net/page/about" "Cows") "."])

(defmacro page
  "Returns top-level HTML for the layout skeleton (main <html> tag, navbar etc.) surrounding `rest."
  [title & rest]
  [{:headers {"Content-Type" "text/html;charset=UTF-8"}}
   `(try
     (str (doctype :xhtml-strict)
          (html [:html {:xmlns "http://www.w3.org/1999/xhtml"}
                 [:head
                  [:title (str ~config/*site-name* (when ~title (str " :: "  ~title)))]
                  (include-css "/combined.css")
                  (include-js "/combined.js")
                  [:meta {:http-equiv "Content-Type"
                          :content "text/html;charset=utf-8"}]
                  [:link {:rel "alternate" :type "application/rss+xml" :href "/feed"}]
                  ]
                 [:body
                  [:div#doc4.yui-t5
                   [:div#hd]
                   [:div#bd
                    [:div#yui-main
                     [:div#main.yui-b.main
                      ~@rest]]
                    [:div#navbar.yui-b
                     (navbar)]]
                   [:div#ft (footer)]]]])))])


(defn to-int [x]
  (when x
    (bigint x)))

(defn paginate
  "Returns a certain number of elements of coll based on config/*posts-per-page* and the current global/*param*."
  [coll]
  (let [start (or (and (:p global/*param*)
                       (re-matches #"^\d+$" (:p global/*param*))
                       (* config/*posts-per-page* (dec (bigint (:p global/*param*)))))
                  0)]
    (take config/*posts-per-page* (drop start coll))))

(defn- param-string [s]
  (str "?" s))

(defn pagenav
  "Returns HTML for a pagination navigation block: a numbered list of links (page 1, page 2 etc.)  This helps us break up huge lists of hundreds of items into smaller paginated lists of a fixed number of items."
  ([coll]
     (pagenav coll param-string))
  ([coll f]
     (let [curr (or (to-int (:p global/*param*)) 1)
           last-page (inc (bigint (/ (count coll) config/*posts-per-page*)))]
       (block nil
              [:div.pagenav
               [:span (str "Page " curr " of " last-page)]
               (if (> curr 1)
                 (link-to (f "p=1") "&laquo; First"))
               (if (> curr 1)
                 (link-to (str (f "p=") (dec curr)) "&laquo; Prev"))
               (map (fn [x] (if (= curr x)
                              [:span.num x]
                              [:a.num {:href (str (f "p=") x)} x]))
                    (filter #(and (>= % 1)
                                  (<= % last-page)) (range (- curr 5) (+ curr 5))))

               (if (< curr last-page)
                 (link-to (str (f "p=") (inc curr)) "Next &raquo;"))
               (if (< curr last-page)
                 (link-to (str (f "p=") last-page) "Last &raquo;"))
               [:div.clear]]))))
