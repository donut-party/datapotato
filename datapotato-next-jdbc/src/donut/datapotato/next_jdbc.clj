(ns donut.datapotato.next-jdbc
  "Convenience configuration that should cover most use cases for inserting data
  with next-jdbc"
  (:require
   [donut.datapotato.core :as dc]
   [next.jdbc :as jdbc]
   [next.jdbc.sql :as sql]))

;;---
;; fixtures
;;---

(comment
  ;; keys:
  ;; dbspec
  ;; dbtype
  ;; insert
  ;; get-inserted
  ;; setup
  ;; teardown
  )

(defmulti get-inserted
  "retrieve data from the db after it's been inserted to account for e.g.
  auto-incrementing ids. Takes a map of:

  * :dbtype - \"postgres\", \"mysql\", etc. if present, used to dispatch to
    method
  * :dbspec - used to connect to a database if needed
  * :connection - the active connection created by with-fixtures
  * :table-name - name of the table that record was inserted into
  * :insert-result - return value of sql/insert!"
  (fn get-inserted-dispatch [{:keys [dbtype dbspec]}]
    (or dbtype (:dbtype dbspec))))

(defmethod get-inserted
  "sqlite"
  [{:keys [connection table-name insert-result]}]
  (first (sql/query connection [(str "SELECT * FROM " table-name "  WHERE rowid = ?")
                                (-> insert-result vals first)])))

(defmethod get-inserted
  :default
  [{:keys [insert-result]}]
  insert-result)

(defn insert
  "inserts a single record in the next.jdbc db for an ent"
  [{{:keys [connection dbspec dbtype]} dc/fixtures-visit-key
    :as                                potato-db}
   {:keys [ent-name ent-type visit-val]}]
  (let [get-inserted_ (or (get-in potato-db [dc/fixtures-visit-key :get-inserted])
                          get-inserted)
        table-name    (get-in (dc/ent-schema potato-db ent-name)
                              [dc/fixtures-visit-key :table-name])]

    (when-not connection
      (throw (ex-info "connection required" {})))

    (when-not table-name
      (throw (ex-info (format "No table name provided. Add under [:schema %s :fixtures :table-name]" ent-type)
                      {:ent-name ent-name
                       :ent-type ent-type})))

    (let [insert-result (sql/insert! connection table-name visit-val)]
      (get-inserted_ {:dbspec        dbspec
                      :dbtype        dbtype
                      :connection    connection
                      :table-name    table-name
                      :insert-result insert-result}))))

(def config
  "Good defaults for configuring database insertion with next-jdbc

  Use this value under the `:fixtures` key in your potatodb, e.g.
  (def potato-db
   {:schema schema
    :generate {:generator mg/generate}
    :fixtures donut.datapotato.next-jdbc/config})"
  {:insert           insert
   :get-connection   (fn next-jdbc-get-connection [potato-db]
                       (jdbc/get-connection (get-in potato-db [:fixtures :dbspec])))
   :close-connection (fn next-jdbc-close-connection [connection] (.close connection))})
