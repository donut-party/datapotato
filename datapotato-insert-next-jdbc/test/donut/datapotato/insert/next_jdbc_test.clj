(ns donut.datapotato.insert.next-jdbc-test
  (:require
   [clojure.test :refer [deftest is]]
   [next.jdbc :as jdbc]
   [next.jdbc.sql :as sql]
   [donut.datapotato.generate.malli :as ddgm]
   [donut.datapotato.insert.next-jdbc :as ddin]))

(def db-spec
  {:dbtype         "sqlite"
   :connection-uri "jdbc:sqlite::memory:"})

(def ID pos-int?)

(def User
  [:map
   [:id ID]
   [:username [:enum "Luigi"]]])

(def Todo
  [:map
   [:id ID]
   [:todo-title string?]
   [:created-by-id ID]
   [:updated-by-id ID]])


(def TodoList
  [:map
   [:id ID]
   [:created-by-id ID]
   [:updated-by-id ID]])

(def schema
  {:user      {:generate {:schema User}
               :insert   {:table-name "users"}
               :prefix   :u}
   :todo      {:generate  {:schema     Todo
                           :overwrites {:todo-title "write unit tests"}}
               :insert    {:table-name "todos"}
               :relations {:created-by-id [:user :id]
                           :updated-by-id [:user :id]
                           :todo-list-id  [:todo-list :id]}
               :prefix    :t}
   :todo-list {:generate  {:schema TodoList}
               :insert    {:table-name "todo_lists"}
               :relations {:created-by-id [:user :id]
                           :updated-by-id [:user :id]}
               :prefix    :tl}})

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
    (is (= [#:users{:id 1}]
           (sql/query conn ["SELECT * FROM users"])))))
