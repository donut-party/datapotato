(ns donut.datapotato.fixtures.next-jdbc-test
  (:require
   [clojure.test :refer [deftest is use-fixtures]]
   [donut.datapotato.core :as dc]
   [donut.datapotato.generate-test :as dgt]
   [donut.datapotato.fixtures.next-jdbc :as dfn]
   [malli.generator :as mg]
   [next.jdbc :as jdbc]
   [next.jdbc.sql :as sql])
  (:import (io.zonky.test.db.postgres.embedded EmbeddedPostgres)))

(def test-connectable (atom nil))
(defonce embedded-pg (future (EmbeddedPostgres/start)))

(def ^:private test-postgres {:dbtype "embedded-postgres" :dbname "clojure_test"})
(def ^:private test-sqlite-mem {:dbtype "sqlite" :connection-uri "jdbc:sqlite::memory:"})

(def test-db-specs
  [test-postgres
   test-sqlite-mem])

(defn with-test-db
  [t]
  (doseq [db test-db-specs]
    (if (= "embedded-postgres" (:dbtype db))
      (reset! test-connectable
              (.getPostgresDatabase ^EmbeddedPostgres @embedded-pg))
      (reset! test-connectable db))
    (t)))

(use-fixtures :each with-test-db)

(def ID
  [:and {:gen/gen dgt/monotonic-id-gen} pos-int?])

(def User
  [:map
   [:users/id ID]
   [:users/username [:enum "Luigi"]]])

(def Todo
  [:map
   [:todos/id ID]
   [:todos/todo_title string?]
   [:todos/created_by_id ID]
   [:todos/updated_by_id ID]])


(def TodoList
  [:map
   [:todo_lists/id ID]
   [:todo_lists/created_by_id ID]
   [:todo_lists/updated_by_id ID]])


(def schema
  {:user      {:prefix   :u
               :generate {:schema User}
               :fixtures {:table-name "users"}}
   :todo      {:generate  {:overwrites {:todos/todo_title "write unit tests"}
                           :schema     Todo}
               :fixtures  {:table-name "todos"}
               :relations {:todos/created_by_id [:user :users/id]
                           :todos/updated_by_id [:user :users/id]
                           :todos/todo_list_id  [:todo-list :todo_lists/id]}
               :prefix    :t}
   :todo-list {:generate  {:schema TodoList}
               :fixtures  {:table-name "todo_lists"}
               :relations {:todo_lists/created_by_id [:user :users/id]
                           :todo_lists/updated_by_id [:user :users/id]}
               :prefix    :tl}})

(defn create-tables
  [conn]
  (doseq [table-name ["users" "todo_lists" "todos"]]
    (try
      (jdbc/execute! conn [(str "DROP TABLE " table-name)])
      (catch Exception _)))

  (jdbc/execute!
   conn
   ["CREATE TABLE users (
       id integer PRIMARY KEY,
       username text NOT NULL
    )"])

  (jdbc/execute!
   conn
   ["CREATE TABLE todo_lists (
       id integer PRIMARY KEY,
       created_by_id INTEGER,
       updated_by_id INTEGER
    )"])

  (jdbc/execute!
   conn
   ["CREATE TABLE todos (
       id integer PRIMARY KEY,
       todo_list_id INTEGER,
       todo_title text NOT NULL,
       created_by_id INTEGER,
       updated_by_id INTEGER
    )"])
  )

(defn ent-db
  []
  {:schema   schema
   :generate {:generator mg/generate}
   :fixtures {:connectable    @test-connectable
              :perform-insert dfn/perform-insert
              :get-connection (fn [] dfn/*connection*)
              :setup          (fn [connection]
                                (create-tables connection)
                                (reset! dgt/id-seq 0))}})

(deftest inserts-simple-generated-data
  (dfn/with-fixtures (ent-db)
    (dc/insert-fixtures (ent-db) {:user [{:num 2}]})
    (is (= [#:users{:id 1 :username "Luigi"}
            #:users{:id 2 :username "Luigi"}]
           (sql/query dfn/*connection* ["SELECT * FROM users"])))))

(deftest inserts-generated-data-hierarchy
  (dfn/with-fixtures (ent-db)
    (dc/insert-fixtures (ent-db) {:todo [{:num 2}]})
    (is (= [#:users{:id 1 :username "Luigi"}]
           (sql/query dfn/*connection* ["SELECT * FROM users"])))

    (is (= [#:todos{:id            5,
                    :todo_list_id  2
                    :todo_title    "write unit tests"
                    :created_by_id 1
                    :updated_by_id 1}
            #:todos{:id            8
                    :todo_list_id  2
                    :todo_title    "write unit tests"
                    :created_by_id 1
                    :updated_by_id 1}]
           (sql/query dfn/*connection* ["SELECT * FROM todos"])))

    (is (= [#:todo_lists{:id            2
                         :created_by_id 1
                         :updated_by_id 1}]
           (sql/query dfn/*connection* ["SELECT * FROM todo_lists"])))))
