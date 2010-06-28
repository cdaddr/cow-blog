(defproject cow-blog "0.2.0-SNAPSHOT"
  :description "Just another blog engine, with cows"
  :url "http://github.com/briancarper/cow-blog"
  :dependencies [[org.clojure/clojure "1.2.0-master-SNAPSHOT"]
                 [org.clojure/clojure-contrib "1.2.0-SNAPSHOT"]
                 [gaka "0.1.0"]
                 [compojure "0.4.0-RC3"]
                 [hiccup "0.2.4"]
                 [clout "0.2.0"]
                 [ring/ring-jetty-adapter "0.2.0"]
                 [ring/ring-devel "0.2.0"]
                 [oyako "0.1.1"]
                 [postgres-pool "1.1.0"]
                 [joda-time "1.6"]
                 [rhino/js "1.7R2"]
                 [sandbar/sandbar "0.2.3"]]
  :dev-dependencies [[swank-clojure "1.2.1"]])
