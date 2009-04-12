(ns net.briancarper.blog.global)

;; server.clj will bind these (thread-locally) to give us global access to
;; various things we'd otherwise have to constantly pass around between functions.
(def *session* nil)
(def *param* nil)
(def *request* nil)

(defmacro if-logged-in
  "If the user is logged in, executes 'rest'.  Otherwise silently does nothing.  NOTE: if you're using if-logged-in to display HTML, make sure there's a single form for 'rest'.  e.g. do this:
  (if-logged-in (list [:p] [:p]))

  not this:

  (if-logged-in [:p] [:p])"
  [& rest]
  `(if (and *session*
            (:username *session*))
     (try ~@rest)))

