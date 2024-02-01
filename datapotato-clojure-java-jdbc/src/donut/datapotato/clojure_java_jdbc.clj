(ns donut.datapotato.clojure-java-jdbc
  "Convenience configuration that should cover most use cases for inserting data
  with clojure.java.jdbc"
  (:require
   [donut.datapotato.core :as dc]
   [clojure.java.jdbc :as jdbc]
   [clojure.string :as str]))

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
  (fn get-inserted-dispatch [{:keys [dbtype dbspec connection]}]
    (or dbtype
        (:dbtype dbspec)
        (-> connection
            .getMetaData
            .getDatabaseProductName
            str/lower-case))))

(defmethod get-inserted
  "sqlite"
  [{:keys [table-name insert-result] :as db}]
  (first (jdbc/query db [(str "SELECT * FROM " table-name "  WHERE rowid = ?")
                         (-> insert-result first ((keyword "last_insert_rowid()")))])))

(defmethod get-inserted
  "oracle"
  [{:keys [table-name insert-result] :as db}]
  (first (jdbc/query db [(str "SELECT * FROM " table-name " WHERE rowid = ?")
                         (:rowid (first insert-result))])))

(defmethod get-inserted
  :default
  [{:keys [insert-result]}]
  (first insert-result))

(defn insert
  "inserts a single record in the clojure.java.jdbc db for an ent"
  [{{:keys [connection]} dc/fixtures-visit-key
    :as                  potato-db}
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

    (let [insert-result (jdbc/insert! connection table-name visit-val)]
      (get-inserted_ (assoc connection
                            :table-name table-name
                            :insert-result insert-result)))))

(def config
  "Good defaults for configuring database insertion with clojure.java.jdbc

  Use this value under the `:fixtures` key in your potatodb, e.g.
  (def potato-db
   {:schema schema
    :generate {:generator mg/generate}
    :fixtures donut.datapotato.next-jdbc/config})"
  {:insert           insert
   :get-connection   (fn clojure-java-jdbc-get-connection [potato-db]
                       (let [dbspec (get-in potato-db [:fixtures :dbspec])]
                         (assoc dbspec :connection (jdbc/get-connection dbspec))))
   :close-connection (fn clojure-java-jdbc-close-connection [connection]
                       (.close (:connection connection)))})
