(ns donut.datapotato.insert.next-jdbc
  (:require
   [donut.datapotato.core :as dc]
   [next.jdbc.sql :as jsql]))

(defn perform-insert
  [{:keys [insert] :as ent-db}
   {:keys [ent-name ent-type visit-val]}]
  (let [{:keys [get-conn get-inserted]} insert
        get-inserted                    (or get-inserted (fn [{:keys [insert-result]}]
                                                           insert-result))
        conn                            (get-conn)
        table-name                      (get-in (dc/ent-schema ent-db ent-name)
                                                [dc/insert-visit-key :table-name])]

    (when-not conn
      (throw (ex-info "db-conn did not return a connection" {:conn conn})))

    (when-not table-name
      (throw (ex-info "no table name provided" {:ent-name ent-name
                                                :ent-type ent-type})))

    (let [insert-result (jsql/insert! conn table-name visit-val)]
      (get-inserted {:conn          conn
                     :table-name    table-name
                     :insert-result insert-result}))))
