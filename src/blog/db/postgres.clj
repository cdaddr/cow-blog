(ns blog.db.postgres
  (:require (clojure.contrib [sql :as sql])
            (net.briancarper [oyako :as oyako])
            (blog [db :as db])))

(defn init-db-postgres []
  (db/with-db
    (let [id [:id "serial primary key"]
          varchar "varchar(255) not null"
          nullchar "varchar(255)"
          text "text not null"
          desc [:name varchar]
          url [:url  varchar]
          tables [[:categories id desc url]
                  [:tags id desc url]
                  [:statuses id desc]
                  [:types id desc]
                  [:posts id url
                   [:category_id "integer default 1 references categories (id) on delete set default"]
                   [:status_id "integer references statuses (id)"]
                   [:type_id "integer references types (id)"]
                   [:title varchar]
                   [:author varchar]
                   [:date_created "timestamp"]
                   [:parent "integer references posts (id)"]
                   [:markdown text]
                   [:html text]]
                  [:comments id
                   [:post_id "integer references posts (id) on delete cascade"]
                   [:status_id "integer references statuses (id)"]
                   [:date_created "timestamp"]
                   [:email nullchar]
                   [:homepage nullchar]
                   [:author varchar]
                   [:markdown text]
                   [:html text]]
                  [:post_tags id
                   [:post_id "integer references posts (id) on delete cascade"]
                   [:tag_id "integer references tags (id) on delete cascade"]]]]
      (doseq [[table & _] (reverse tables)]
        (sql/do-commands (str "drop table if exists " (name table)))
        (println "Dropped" table "(if it existed)."))
      (doseq [[table & specs] tables]
        (apply sql/create-table table specs)
        (println "Created" table)))
    (doseq [[table & cols] [[:posts :category_id :status_id :type_id]
                            [:post_tags :post_id :tag_id]
                            [:comments :post_id :status_id]]
            col cols]
      (sql/do-commands (str "create index " (name table) "_" (name col)
                            " on " (name table) "(" (name col) ")")))
    (println "Added indices")
    (doseq [[table vals] [[:types ["Blog" "Page" "Toplevel"]]
                          [:statuses ["Public" "Draft" "Spam"]]]]
      (apply sql/insert-records table
             (map #(hash-map :name %) vals))
      (println "Initialized" table))
    (sql/insert-records :categories {:id 1 :name "Uncategorized" :url "uncategorized"})
    (println "Initialized" :categories)))

