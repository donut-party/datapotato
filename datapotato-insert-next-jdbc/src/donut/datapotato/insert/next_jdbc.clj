(ns donut.datapotato.insert.next-jdbc
  (:require
   [donut.datapotato.core :as dd]
   [next.jdbc.sql :as jsql]))

(def visit-key :insert)

(defn un-ns-keywords
  [m]
  (reduce-kv (fn [m k v]
               (assoc m
                      (-> k name keyword)
                      v))
             {}
             m))

(defn perform-insert
  [{:keys [insert] :as ent-db}
   {:keys [ent-name ent-type visit-val]}]
  (let [{:keys [get-insert-db get-inserted]} insert
        get-inserted                         (or get-inserted (fn [{:keys [insert-result]}]
                                                                insert-result))
        db                                   (get-insert-db)
        table-name                           (get-in (dd/ent-schema ent-db ent-name) [visit-key :table-name])]

    (when-not db
      (throw (ex-info "get-insert-db did not return db" {:db db})))

    (when-not table-name
      (throw (ex-info "no table name provided" {:ent-name ent-name
                                                :ent-type ent-type})))

    (let [insert-result (jsql/insert! db table-name visit-val)]
      (get-inserted {:db            db
                     :table-name    table-name
                     :insert-result insert-result}))))

(def insert-generated
  (dd/wrap-incremental-insert-visiting-fn :generate perform-insert))

(defn insert [ent-db]
  (-> ent-db
      (dd/visit-ents-once visit-key insert-generated)
      (dd/attr-map visit-key)))

(defn generate-insert
  [ent-db query]
  (-> ent-db
      (dd/generate query)
      (insert)))
