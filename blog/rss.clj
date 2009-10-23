(ns blog.rss
  (:use (compojure.html (gen))
        (blog config db util layout)))

;; RSS

(defn rss [title site-url description & body]
  (html "<?xml version=\"1.0\" encoding=\"UTF-8\" ?>"
        [:rss {:version "2.0"
               :xmlns:content "http://purl.org/rss/1.0/modules/content/"
               :xmlns:wfw "http://wellformedweb.org/CommentAPI/"
               :xmlns:dc " http://purl.org/dc/elements/1.1/"}
         [:channel
          [:title title]
          [:link site-url]

          [:description description]
          body]]))

(defn rss-item [post]
  (html
   [:item
    [:title (:title post)]
    [:link (str SITE-URL (url post))]
    [:guid (str SITE-URL (url post))]
    [:pubDate (rfc822-date (:date post))]
    [:description (escape-html (:html post))]]))

(defn rss-index []
  (rss
      SITE-TITLE
      SITE-URL
      SITE-DESCRIPTION
    (map rss-item (take 25 (all-posts)))))

(defn tag-rss [tagname]
  (if-let [tag (get-tag tagname)]
    (rss
        (str SITE-TITLE " Tag: " (:name tag))
        (str SITE-URL (url tag))
        SITE-DESCRIPTION
      (map rss-item (take 25 (all-posts-with-tag tag))))))

(defn category-rss [catname]
  (if-let [category (get-category catname)]
    (rss
        (str SITE-TITLE " Category: " (:name category))
        (str SITE-URL (url category))
        SITE-DESCRIPTION
      (map rss-item (take 25 (all-posts-with-category category))))))
