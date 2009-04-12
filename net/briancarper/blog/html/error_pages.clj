(ns net.briancarper.blog.html.error-pages
  (use compojure
       (net.briancarper.blog.html [layout :as layout])))

;; Error pages

(defn error
  "Returns HTML for an error page.  If code is given it's used as the HTTP response code (e.g. 403, 404)."
  ([code msg]
     [code (error msg)])
  ([msg]
     (page "Error!"
           (block nil
                  [:h3 (str "ERROR: " msg)]
                  [:p "Sorry, something broke."]
                  [:p "Do you want to go back to the " (link-to "/" "front page") "?"]
                  [:p "Do you want to look through the " (link-to "/archives" "Archives") "?"]))))

(defn error-404
  "Returns HTML for a 404 error page."
  []
  (error 404 "404 - Not Found!"))

