(ns donut.datapotato.fixtures.next-jdbc
  (:require
   [donut.datapotato.core :as dc]
   [next.jdbc :as jdbc]
   [next.jdbc.sql :as sql]))

(comment
  ;; keys:
  ;; dbspec
  ;; dbtype
  ;; perform-insert
  ;; get-inserted
  ;; setup
  ;; teardown
  )

(defmulti get-inserted
  "default for retrieving data from the db after it's been inserted"
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

(defn perform-insert
  [{{:keys [connection dbspec dbtype]} dc/fixtures-visit-key
    :as                                ent-db}
   {:keys [ent-name ent-type visit-val]}]
  (let [get-inserted_ (or (get-in ent-db [dc/fixtures-visit-key :get-inserted])
                          get-inserted)
        table-name    (get-in (dc/ent-schema ent-db ent-name)
                              [dc/fixtures-visit-key :table-name])]

    (when-not connection
      (throw (ex-info "connection required" {})))

    (when-not table-name
      (throw (ex-info "no table name provided" {:ent-name ent-name
                                                :ent-type ent-type})))

    (let [insert-result (sql/insert! connection table-name visit-val)]
      (get-inserted_ {:dbspec        dbspec
                      :dbtype        dbtype
                      :connection    connection
                      :table-name    table-name
                      :insert-result insert-result}))))

(def config
  {:perform-insert  perform-insert
   :open-connection #(jdbc/get-connection (get-in % [:fixtures :dbspec]))})
