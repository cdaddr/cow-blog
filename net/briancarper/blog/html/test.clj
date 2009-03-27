(ns net.briancarper.blog.html.test
  (use (net.briancarper.blog html)))

(in-ns 'net.briancarper.blog.html)

(defmacro logged-in [& rest]
  `(binding [*session* (ref {:username "foo"})]
     ~@rest))