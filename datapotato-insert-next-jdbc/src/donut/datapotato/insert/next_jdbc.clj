(ns donut.datapotato.insert.next-jdbc
  (:require
   [donut.datapotato.core :as dd]
   [next.jdbc.sql :as jsql]))

(def visit-key :insert)

(defn perform-insert
  [db]
  (fn [ent-db {:keys [ent-name visit-val]}]
    (jsql/insert! db
                  (get-in (dd/ent-schema ent-db ent-name) [visit-key :table-name])
                  visit-val)))

(defn insert-generated
  [db]
  (dd/wrap-incremental-insert-visiting-fn :generate (perform-insert db)))

(defn insert [ent-db db]
  (-> ent-db
      (dd/visit-ents-once visit-key (insert-generated db))
      (dd/attr-map visit-key)))
