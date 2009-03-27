;; CRUD - Create/Read/Update/Delete library for Clojure
;; by Brian Carper - http://briancarper.net

;; The point of this library is:

;;   1. To further abstract clojure.contrib.sql so you don't have to write any SQL manually.

;;   2. To read data from SQL into Clojure ref's, so that data can be quickly fetched from memory without touching the DB.

;;   3. To provide hooks for pre- and post- update/create/delete (sort of Ruby-on-Rails-like, kind of, if you squint a bit).

;;   4. Abstract "SELECT" via multimethods to easily select based on integer or string ID, map of column/value selection criteria, string, etc.

;; See test.clj for examples.
;; 

(ns net.briancarper.crud
  (:use (clojure.contrib sql str-utils)))

(defonce db nil)
(defonce data nil)

(defn- remove-empty
  "Takes a map, returns a map with empty strings replaced with nils"
  [h]
  (into {}
        (map (fn [[k v]] (if (= v "") nil [k v]))
             h)))

(defn- key-vals
  "Takes a map and some keys, returns a map containing only those keys."
  [h & keys]
  (doall (map #(get h %) keys)))

(defn- flat-map
  "Takes a map, returns a flattened sequence of that map's keys/vals"
  [h]
  (apply concat (seq h)))

(defn- only-keys
  ""
  [h & keys]
  (apply hash-map
         (mapcat (fn [x] (when (contains? h x) [x (get h x)]))
                 keys)))

(defmacro with-db
  "Helper macro; wraps a bunch of forms in a with-connection and transaction."
  [& rest]
  `(if db
    (with-connection db
      (transaction
        ~@rest))
    (throw (Exception. "Please bind net.briancarper.crud/db and net.briancarper.crud/data before calling this fn.  See notes in crud.clj for examples."))))

(defn normalize-table-name
  "If obj is a String, returns it; otherwise returns (name obj)."
  [obj]
  (cond
    (string? obj) obj
    :else (str (name obj))))

(defn normalize-id
  "Returns id as a BigInt."
  [id]
  (bigint id))

(defn- normalize-row [table row]
  (assoc row
    :table table
    :id (normalize-id (:id row))))

(defn fix-obj-id [obj]
  (assoc obj :id (normalize-id (:id obj))))

(defn- select-all
  "Runs a query; returns a vector of hash-maps of the results."
  [query]
  (with-query-results
      rows
      query
    (into [] rows)))

(defn- select-one
  "Runs a query; if more than one result row is returned, throws an exception.  Otherwise returns the result row as a hashmap."
  [query]
  (let [rows (select-all query)
        c (count rows)]
    (if (not (= 1 c))
      (throw (new Exception (str "QUERY FAILED: Expected 1 result, got " c " in " query)))
      (first rows))))

(defn last-insert-id
  "Returns the database ID for the last inserted row."
  []
  (normalize-id
   (:id (select-one ["SELECT LAST_INSERT_ID() as id"]))))

(defn- columns
  "Returns the result of a DESCRIBE query on a table."
  [table-name]
  (select-all [(str "DESCRIBE " (normalize-table-name table-name) ";")]))

(defn column-names
  "Returns the names of all columns in table-name as a seq of Keywords."
  [table-name]
  (with-db
   (map #(keyword (:column_name %)) (columns table-name))))

(defn- add-table [table obj]
  (assoc obj :table (keyword table)))

(defn- find-one
  "Given a table with an `id` column, and an ID, fetch the row from that table having that ID; returns a map representing the result.  If more than one result row is returned, throws an exception."
  ([table id]
     (let [table-name (normalize-table-name table)
           id (normalize-id id)]
       (with-db
        (normalize-row table
                       (select-one [(str "SELECT * FROM " table-name " WHERE id = ?")
                                    id]))))))

(defn- find-all
  "Returns all rows from a table."
  ([table]
     (let [table-name (normalize-table-name table)]
       (with-db
        (doall
         (map (partial normalize-row table)
              (select-all [(str "SELECT * FROM " table-name)])))))))

;;;; PUBLIC API

;; Hooks timeline:
;;
;;   before-save before-create CREATE after-create after-save after-init REF after-ref-update
;;   before-save before-update UPDATE after-update after-save after-init REF after-ref-update
;;   before-delete DELETE REF after-ref-update after-delete
;;   RELOAD after-init REF after-ref-update


(defmulti after-all :table)
(defmulti before-ref :table)

(defmulti before-save :table)
(defmulti after-save :table)

(defmulti before-create :table)
(defmulti after-create :table)

(defmulti before-update :table)
(defmulti after-update :table)

(defmulti before-delete :table)
(defmulti after-delete :table)

(defmethod before-save :default [obj] obj)
(defmethod after-save :default [obj] obj)
(defmethod before-create :default [obj] obj)
(defmethod after-create :default [obj] obj)
(defmethod before-update :default [obj] obj)
(defmethod after-update :default [obj] obj)
(defmethod before-delete :default [obj] obj)
(defmethod after-delete :default [obj] obj)

(defmethod before-ref :default [obj] obj)
(defmethod after-all :default [obj] obj)


(defn create
  "Given a hash-map obj of columns => values, insert a new row into a DB for that obj into some table.  Any keys in `obj` which don't name valid columns in `table` are discarded. `table` defaults to (:table obj).  `update-ref?` if given determines whether the data ref is updated after the DB insert; default is true.  Returns a new hash-map containing values for ALL columns from the DB, and :id and :table tags." 
  ([obj]
     (create (:table obj) obj true))
  ([table obj] (create table obj true))
  ([table obj update-ref?]
     (with-db
      (let [cols (column-names table)
            obj (before-save
                 (before-create
                  (assoc obj :table table)))]
        (insert-records (normalize-table-name table)
                          (apply only-keys (remove-empty obj) cols))
        (let [new-id (last-insert-id)
              new-obj (before-ref
                       (normalize-row table
                                      (find-one table new-id)))]
          (if update-ref?
            (let []
              (dosync
               (alter (data table) assoc new-id new-obj))
              (after-all (after-create new-obj))
              new-obj)
            new-obj))))))

(defn update
    "Given a hash-map obj of columns => values, update a row in the DB.  obj MUST have an :id field; this is used to determine which row to update.  Any keys in `obj` which don't name valid columns in `table` are discarded.  table defaults to (:table obj).  update-ref? if given determines whether the data ref is updated after the DB insert; default is true.  Returns a new hash-map containing ALL colummns from the DB and :id and :table tags."
  ([obj]
     (update (:table obj) obj))
  ([table obj]
     (with-db
      (let [cols (column-names table)
            obj (before-save
                 (before-update obj))]
        (update-values table
                       ["id = ?" (:id obj)]
                       (apply only-keys obj cols))
        (let [new-obj (before-ref
                       (find-one table (:id obj)))]
          (dosync
           (alter (data table) assoc (:id obj) new-obj))
          (after-all (after-update new-obj))
          new-obj)))))

(defn delete
  "Given a hash-map with an :id key, delete a row from `table` with that key.  `table` defaults to (:table obj).  Returns the number of rows deleted (which should always be 0 or 1)."
  ([obj]
     (delete (:table obj) obj))
  ([table obj]
     (let [obj (before-delete obj)]
       (with-db
        (try
         (let [num-deleted (first (delete-rows table ["id = ?" (:id obj)]))]
           (dosync
            (alter (data table) dissoc (:id obj)))
           (after-all (after-delete obj))
           num-deleted)
         
         (catch Exception e
           (throw (Exception. (str "Error deleting " obj ": " (str e))))))))))

;; FETCH - these methods fetch from the data ref and don't touch the DB.

(defn- fetch-dispatcher [table x]
  (if data
    (cond
      (associative? x) :keyval
      (integer? x) :id
      (nil? x) :nil
      :else table ;;(throw (Exception. (str "Invalid fetch criteria: " x)))
      )
    (throw (Exception. "Please bind net.briancarper.crud/data to a ref.  See crud.clj for examples."))))

(defmulti fetch
  "Returns an object from table by fetching from a data-ref (no DB queries)."
  fetch-dispatcher)

(defmulti fetch-all
  (fn
    ([table] :all)
    ([table x] (fetch-dispatcher table x))))

(defmethod fetch-all :all [table]
  (if-let [t (data table)]
    (vals @t)))

(defmethod fetch-all :keyval [table h]
  (if-let [t (data table)]
    (filter (fn [post]
              (every? (fn [[k v]]
                        (= (k post) v))
                      h))
            (vals @t))))

(defmethod fetch-all :id [table id]
  [(fetch table id)])

(defmethod fetch-all :nil [& rest]
  nil)


(defmethod fetch :keyval [table h]
  (first
   (filter (fn [post]
             (every? (fn [[k v]]
                       (= (k post) v))
                     h))
           (fetch-all table)) ))

(defmethod fetch :id [table id]
  (if-let [t (data table)]
    (@t (normalize-id id))))

(defmethod fetch :nil [& rest]
  nil)

(defn fetch-or-create
  "Given a hash-map obj of columns => vals, returns an obj from the data ref matching those vals if it exists; otherwise creates a new row with those vals."
  ([obj] (fetch-or-create (:table obj) obj))
  ([table obj]
     (if-let [val (fetch table obj)]
       val
       (create table obj))))


(defn reload
  "Reloads the data ref with fresh data from the DB.  Use this if you update the DB by some means other than calling fns in this class (e.g. manual DB query) and want to refresh the data ref to match.  Returns the obj."
  [obj]
  (with-db
   (let [table (:table obj)
         id (:id obj)
         new-obj (before-ref
                  (find-one table id))]
     (dosync
      (alter (data table) assoc id new-obj))
     (after-all new-obj)
     new-obj)))

(defn init
  "Initialize the data ref by slurping up all the data from some table.  Returns the number of records found."
  [table]
  (with-db
   (let [rows (map before-ref (find-all table))
         row-map (apply hash-map
                        (mapcat #(vector (:id %) %)
                                rows))]
     (dosync
      (alter data assoc table (ref row-map)))
     (count rows))))