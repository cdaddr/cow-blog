(ns blog.rss
  (:use (hiccup [core :only [html escape-html]]))
  (:require (blog [config :as config]
                  [db :as db]
                  [util :as util]
                  [layout :as layout]
                  [link :as link]
                  [time :as time]
                  [html :as html])))

;; RSS

(defn rss [title site-url description & body]
  {:headers {"Content-Type" "text/xml; charset=utf-8"}
   :body (html "<?xml version=\"1.0\" encoding=\"UTF-8\" ?>"
               [:rss {:version "2.0"
                      :xmlns:content "http://purl.org/rss/1.0/modules/content/"
                      :xmlns:wfw "http://wellformedweb.org/CommentAPI/"
                      :xmlns:dc " http://purl.org/dc/elements/1.1/"}
                [:channel
                 [:title title]
                 [:link site-url]
                 [:description description]
                 body]])})

(defn rss-item [post]
  [:item
   [:title (:title post)]
   [:link (str config/SITE-URL (link/url post))]
   [:guid (str config/SITE-URL (link/url post))]
   [:pubDate (time/datestr :http (:date post))]
   [:description (escape-html (html (html/post-body post :front-page? true)))]])

(defn posts []
  (rss
      config/SITE-TITLE
      config/SITE-URL
      config/SITE-DESCRIPTION
    (map rss-item (db/posts :limit 25 :offset 0))))
 
(defn tag [tagname]
  (comment
   (if-let [tag (db/tag tagname :limit 25)]
     (rss
         (str config/SITE-TITLE " (Tag: " (:title tag) ")")
         (str config/SITE-URL (link/url tag))
         config/SITE-DESCRIPTION
       (map rss-item (:posts tag))))))

(defn category [catname]
  (comment
   (if-let [category (db/category catname :limit 25)]
     (rss
         (str config/SITE-TITLE " (Category: " (:title category) ")")
         (str config/SITE-URL (link/url category))
         config/SITE-DESCRIPTION
       (map rss-item (:posts category))))))
