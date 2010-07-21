(ns blog.css
  (:require (gaka [core :as gaka])))

(def no-mp (list :margin 0 :padding 0))
(def no-border (list :border "none"))
(def std-size (list :font-size "12px"))
(def dotted-underline (list :text-decoration "none"
                            :border-bottom "1px dotted #808080"))
(def solid-underline (list :text-decoration "none"
                           :border-bottom "1px solid #808080"))
(def no-underline (list :text-decoration "none"
                        :border-bottom "none"))
(def title (list :font-weight "bold"
                 :font-size "14px"
                 :color "#333"
                 :font-style "normal"))
(def message-box (list :color "black"
                       :padding "5px 0"
                       :margin "10px auto"
                       :font-weight "bold"
                       :text-align "center"))

(def global
     [:body
      :color "black"
      :background "#fff"
      :font-family "Verdana, Arial, Helvetica, sans-serif"
      :font-size "12px"
      std-size
      no-mp
      [:a
       :color "#333"
       no-underline
       [:img no-border]]
      [:a:hover no-underline]
      [:blockquote
       :font-style "italic"
       :margin-left "18px"
       :padding-left "5px"]
      [:pre
       ;:background "#eee"
       ;:font-family "DejaVu Sans Mono,Bitstream Vera Sans Mono,Courier New, monospace"
       ;:margin "15px 20px"
       ;:padding "5px"
       ;:border "1px #ccc solid"
       ;:border-left "10px #ccc solid"
       :overflow "auto"
       :color "#c0c0c0"
       :padding "10px"
       :margin "15px 20px"
       :background "#202020"
       [:code no-border]]
      [:code
       :font-weight "bold"
       :font-family "Consolas, monospace"
       :font-size "10pt"]
      [:.error
       message-box
       :background "#d99383"
       :border "1px #593c35 solid"]
      [:.message
       message-box
       :background "#93d983"
       :border "1px #3c5935 solid"]
      [:#wrapper
       :background-color "#fff"
       :margin-right "auto"
       :margin-left "auto"
       :width "775px"
       :padding "6px"]
      [:div.clear :clear "both"]
      [:.spam :color "red"]
      [:.draft :color "grey"]
      [:.public :color "green"]])

(def header
     [:#header
      :padding "12px 0 16px 0"
      :margin "24px 0"
      [:.desc :float "left"
       [:a :font-style "italic"]]
      [:h1
       :font-family "Georgia, \"Times New Roman\", Times, serif"
       :font-size "34px"
       :color "black"
       :font-weight "normal"
       no-mp
       [:a :color "black"]
       [:a:hover]
       [:.desc
        :float "left"
        [:a :font-size "italic"]
        [:a:hover
         :background-color "#eee"
         :color "#666"]]]])

(def sidebar
     [:#sidebar
      :background "#fff"
      :border-left "1px dotted #ccc"
      :padding "0 10px"
      :float "right"
      :width "144px"
      [:h2
       :font-weight "normal"
       std-size
       no-mp]
      [:ul
       :color "#808080"
       :list-style-type "none"
       :font-size "11px"
       :margin 0
       :padding-left "3px"
       [:li
        :margin-top "10px"
        :padding-bottom "2px"]
       [:ul
        :font-variant "normal"
        :font-weight "normal"
        :list-style-type "none"
        :text-align "left"
        no-mp
        [:li
         no-mp
         no-border
         :padding-left "3px"]]]])

(def forms
     [:form
      [:label
       :display "block"
       :float "left"
       :text-align "right"
       :width "100px"
       :padding-right "5px"]
      [:.submit
       :padding-left "105px"]
      [:textarea
       :width "450px"
       :height "250px"]
      ["input[type=text], textarea"
       :min-width "250px"
       :font-size "100%"
       :font-family "DejaVu Sans Mono,Bitstream Vera Sans Mono,Courier New, monospace"]])

(def posts
     [:.post
      :margin-bottom "15px"
      :padding-bottom "15px"
      [:.title
       title
       no-mp
       :font-style "normal"
       [:a
        :font-weight "bold"
        :font-size "14px"
        no-underline
        no-border]]
      [:.author
       no-mp
       :font-size "10px"
       :color "#808080"
       :font-weight "normal"
       :margin-bottom "10px"]
      [:.parent
       :font-weight "bold"
       :font-style "italic"
       :color "#888"
       :margin-bottom "10px"]])

(def comments
  [:#comments
   :padding-top "18px"
   :padding-bottom "18px"
   [:.title
    title
    :padding-bottom "5px"]
   [:.comment
    :padding "5px"
    [:.author
     :font-size "10px"
     :padding-bottom "8px"
     :color "#808080"]
    [:.gravatar
     :float "left"
     :padding-bottom "8px"
     :padding-right "8px"]
    [:.body
     :margin-left "70px"]
    [:.name
     :color "black"
     :font-weight "bold"]]
   [:.even :background-color "white"]
   [:.odd :background-color "#f0f0f0"]
   [:.test
    :text-align "right"
    [:img :vertical-align "middle"]]
   [:input#test
    :min-width "150px"
    :width "150px"
    :vertical-align "middle"]
   ])

(def pagenav
     [:.pagenav
      :font-size "11px"
      no-mp
      ["a, .navtext, .num"
       :display "block"
       :float "left"
       :padding "3px"
       :margin "2px"
       no-border]])

(def content
     [:#content
      :float "left"
      :width "600px"
      :margin-bottom "-10px"
      [:a dotted-underline]
      [:a:hover solid-underline]
      [:ul :list-style-type "circle"
       [:ul :list-style-type "disc"]]
      [:h1
       :font-size "12px"
       :color "#444"]
      [:h2
       :font-size "12px"
       :margin "0 0 2px 0"
       :font-style "italic"
       :color "#666"
       :padding-bottom "2px"]
      [:h3
       no-mp
       :margin "0 0 5px 0"
       :font-size "12px"
       :color "#888"]
      [:h4]
      ["p, li, .feedback" :font-size "11px"]
      [:p
       no-mp
       :margin-bottom "10px"]
      [:img.rss :vertical-align "middle"]
      [:.meta
       :color "#666"
       :text-align "right"
       :font-size "10px"
       :clear "both"]
      [:a.cloud no-border]
      [:#preview
       :border "1px grey dashed"
       :background "#eee"
       :font-size "93%"
       :min-height "25px"
       :padding "5px"
       [:blockquote :background "#ddd"]]
      
      [:table
       :font-size "11px"
       [:td :padding "3px"]
       [:th.big
        :font-size "14px"
        :border "1px dotted #ccc"]]
      posts
      forms
      comments
      pagenav])

(def footer [:#footer
             :width "600px"
             :font-style "italic"
             :clear "both"
             :color "#666"
             :font-size "10px"
             :padding "25px 0 0 0"
             :margin "0 0 20px 0"
             :text-align "center"
             [:a
              no-underline
              :color "#333"]
             [:a:hover :color "#666"]])

(gaka/save-css "public/css/style.css"
               global header sidebar content footer)
 
