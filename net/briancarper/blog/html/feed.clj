(ns net.briancarper.blog.html.feed
  (:use compojure
        (net.briancarper.blog [config :as config]
                              [db :as db])
        (net.briancarper.blog.html [error-pages :as error-pages])
        (net.briancarper util)))


;; RSS

(defmacro rss [title site-url description & body]
  [{:headers {"Content-Type" "text/xml;charset=UTF-8"}}
   `(html "<?xml version=\"1.0\" encoding=\"UTF-8\" ?>"
          [:rss {:version "2.0"
                 :xmlns:content "http://purl.org/rss/1.0/modules/content/"
                 :xmlns:wfw "http://wellformedweb.org/CommentAPI/"
                 :xmlns:dc " http://purl.org/dc/elements/1.1/"}
           [:channel
            [:title ~title]
            [:link ~site-url]

            [:description ~description]
            ~@body]])])

(defn rss-item [post]
  (html
   [:item
    [:title (:title post)]
    [:link (str *site-url* (:url post))]
    [:guid (str *site-url* (:url post))]
    [:pubDate (rfc822-date (:created post))]
    [:description (escape-html (:html post))]]))

(defn rss-index []
  (rss
      *site-name*
      *site-url*
      *site-description*
      (map rss-item (take 25 (all-blog-posts)))))

(defn comment-rss [id]
  (if-let [post (get-post id)]
    (rss
        *site-name*
        (str *site-url* (:url post) "#comments")
        *site-name* " Comment Feed for Post " (:title post)
      (map rss-item (:comments post)))
    (error-404 )))

(defn tag-rss [tagname]
  (if-let [tag (get-tag tagname)]
    (rss
        (str *site-name* " Tag: " (:name tag))
        (str *site-url* (:url tag))
        *site-description*
        (map rss-item (take 25 (all-posts-with-tag tag))))
    (error-404 )))

(defn category-rss [catname]
  (if-let [category (get-category catname)]
    (rss
        (str *site-name* " Category: " (:name category))
        (str *site-url* (:url category))
        *site-description*
      (map rss-item (take 25 (all-posts-with-category category))))
    (error-404 )))