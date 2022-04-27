(ns donut.datapotato.insert.next-jdbc-test
  (:require
   [clojure.test :refer [deftest is]]
   [next.jdbc :as jdbc]
   [next.jdbc.sql :as sql]
   [donut.datapotato.generate.malli :as ddgm]
   [donut.datapotato.generate.malli-test :as ddgmt]
   [donut.datapotato.insert.next-jdbc :as ddin]
   [donut.datapotato.test-data :as td]))

(def db-spec
  {:dbtype         "sqlite"
   :connection-uri "jdbc:sqlite::memory:"})

(def schema
  (-> ddgmt/schema
      (assoc-in [:user :insert :table-name] "users")
      (assoc-in [:todo :insert :table-name] "todos")
      (assoc-in [:todo-list :insert :table-name] "todo_lists")))

(defn gen-insert
  [ent-db db query]
  (-> (ddgm/generate ent-db query)
      (ddin/insert db)))

(defn create-tables
  [conn]
  (jdbc/execute!
   conn
   ["CREATE TABLE users (
       id integer PRIMARY KEY,
       username text NOT NULL
    )"]))

(deftest inserts-generated-data
  (with-open [conn (jdbc/get-connection db-spec)]
    (create-tables conn)
    (gen-insert {:schema schema} conn {:user [[2]]})
    (is (= [#:users{:id 1 :username "Luigi"}
            #:users{:id 2 :username "Luigi"}]
           (sql/query conn ["SELECT * FROM users"])))
    (reset! td/id-seq 0)))
