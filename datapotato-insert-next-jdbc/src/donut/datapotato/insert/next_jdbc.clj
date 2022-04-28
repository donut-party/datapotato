(ns donut.datapotato.insert.next-jdbc
  (:require
   [donut.datapotato.core :as dd]
   [next.jdbc.sql :as jsql]))

(def visit-key :insert)

(defn perform-insert
  [{:keys [get-insert-db] :as ent-db}
   {:keys [ent-name visit-val]}]
  (jsql/insert! (get-insert-db)
                (get-in (dd/ent-schema ent-db ent-name) [visit-key :table-name])
                visit-val))

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
