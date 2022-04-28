(ns donut.datapotato.insert.next-jdbc-test
  (:require
   [clojure.test :refer [deftest is]]
   [donut.datapotato.generate.malli-test :as ddgmt]
   [donut.datapotato.test-schemas :as ts]
   [donut.datapotato.insert.next-jdbc :as ddin]
   [malli.generator :as mg]
   [next.jdbc :as jdbc]
   [next.jdbc.sql :as sql]))

(def db-spec
  {:dbtype         "sqlite"
   :connection-uri "jdbc:sqlite::memory:"})

(def schema
  (-> ddgmt/schema
      (assoc-in [:user :insert :table-name] "users")
      (assoc-in [:todo :insert :table-name] "todos")
      (assoc-in [:todo-list :insert :table-name] "todo_lists")))

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
    (ddin/generate-insert {:schema        schema
                           :generator     mg/generate
                           :get-insert-db (constantly conn)}
                          {:user [[2]]})
    (is (= [#:users{:id 1 :username "Luigi"}
            #:users{:id 2 :username "Luigi"}]
           (sql/query conn ["SELECT * FROM users"])))
    (reset! ts/id-seq 0)))
