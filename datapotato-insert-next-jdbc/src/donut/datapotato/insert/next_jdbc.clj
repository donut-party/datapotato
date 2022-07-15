(ns donut.datapotato.insert.next-jdbc
  (:require
   [donut.datapotato.core :as dc]
   [next.jdbc.sql :as jsql]))

(defn perform-insert
  [{:keys [insert] :as ent-db}
   {:keys [ent-name ent-type visit-val]}]
  (let [{:keys [connection get-inserted]} insert
        get-inserted                      (or get-inserted (fn [{:keys [insert-result]}]
                                                             insert-result))
        table-name                        (get-in (dc/ent-schema ent-db ent-name)
                                                  [dc/insert-visit-key :table-name])]

    (when-not connection
      (throw (ex-info "connection required" {})))

    (when-not table-name
      (throw (ex-info "no table name provided" {:ent-name ent-name
                                                :ent-type ent-type})))

    (let [insert-result (jsql/insert! connection table-name visit-val)]
      (get-inserted {:connection    connection
                     :table-name    table-name
                     :insert-result insert-result}))))

(def ^:dynamic *connection*)

(defmacro with-db
  [ent-db & body]
  `(let [ent-db# ~ent-db]
     (with-open [conn# (or (get-in ent-db# [:insert :connection])
                           (jdbc/get-connection (get-in ent-db# [:insert :db-spec])))]
       (binding [*connection* conn#]
         (when-let [setup# (get-in ent-db# [:insert :setup])])
         ~@body))))
