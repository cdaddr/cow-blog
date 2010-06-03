(ns blog.flash
  (:require (sandbar [stateful-session :as session])))

(defn message [x]
  (session/set-flash-value! :message x))

(defn error [x]
  (session/set-flash-value! :error x))
