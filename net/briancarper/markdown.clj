;; markdown - Markdown-to-HTML library for Clojure
;; by Brian Carper - http://briancarper.net

;; This library converts Markdown-formatted text into HTML using Showdown and Rhino.

;; Showdown: http://attacklab.net/showdown/
;; Rhino:    http://www.mozilla.org/rhino/

;; I have edited showdown.js a bit to include a "safe"-mode which escapes all HTML
;; (note, this hasn't been tested in extreme conditions, and probably isn't very
;; safe at all!).

;; 

(ns net.briancarper.markdown
  (:use (clojure.contrib str-utils shell-out))
  (:import (org.mozilla.javascript Context ScriptableObject)))

(defn- tempfile []
  (java.io.File/createTempFile "foo" ""))

(defmacro with-tmp-file [[file text] & rest]
  `(let [tmp# (tempfile)]
     (try
      (with-open [f# (java.io.FileWriter. tmp#)]
        (.append f# ~text))
      (let [~file (.getAbsolutePath tmp#)]
        ~@rest)
      (finally
        (.delete tmp#)))))

;; NOTE: Edit to point to showdown.js

(defn markdown-to-html [txt safe]
  (let [cx (Context/enter)
        scope (.initStandardObjects cx)
        input (Context/javaToJS txt scope)
        script (str (slurp "static-blog/js/showdown.js")
                    "new Showdown.converter().makeHtml(input, " (if safe "true" "false") ");")]
    (try
     (ScriptableObject/putProperty scope "input" input)
     (let [result (.evaluateString cx scope script "<cmd>" 1 nil)]
       (Context/toString result))
     (finally (Context/exit)))))

(comment
  ;;examples
  (markdown-to-html "*foo <br/> bar*" false) ; => "<p><em>foo <br/> bar</em></p>"
  (markdown-to-html "*foo <br/> bar*" true)  ; => "<p><em>foo &lt;br/> bar</em></p>"
  )