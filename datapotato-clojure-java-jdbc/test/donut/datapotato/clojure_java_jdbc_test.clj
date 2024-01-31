(ns donut.datapotato.clojure-java-jdbc-test
  (:require
   [clojure.java.jdbc :as jdbc]
   [clojure.test :refer [deftest is use-fixtures testing]]
   [donut.datapotato.clojure-java-jdbc :as dnj]
   [donut.datapotato.core :as dc]
   [donut.datapotato.generate-test :as dgt]
   [malli.generator :as mg]
   [matcher-combinators.test])
  (:import
   (io.zonky.test.db.postgres.embedded EmbeddedPostgres)))

;;---
;; connections for different dbs
;;---

(def test-dbspec (atom nil))
(defonce embedded-pg (future (EmbeddedPostgres/start)))

(def ^:private test-postgres
  {:dbtype "embedded-postgres"
   :dbname "clojure_test"})
(def ^:private test-sqlite-mem
  {:dbtype         "sqlite"
   :connection-uri "jdbc:sqlite::memory:"})

(def test-db-specs
  [test-postgres
   test-sqlite-mem])

(defn with-test-db
  [t]
  (doseq [db test-db-specs]
    (if (= "embedded-postgres" (:dbtype db))
      (reset! test-dbspec
              (assoc db :datasource (.getPostgresDatabase ^EmbeddedPostgres @embedded-pg)))
      (reset! test-dbspec db))
    (t)))

(use-fixtures :each with-test-db)

(defn create-tables
  "used by datapotato's own fixture helper"
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

;;---
;; schemas
;;---

(def ID
  [:and {:gen/gen dgt/monotonic-id-gen} pos-int?])

(def User
  [:map
   [:id ID]
   [:username string?]])

(def Todo
  [:map
   [:id ID]
   [:todo_title string?]
   [:created_by_id ID]
   [:updated_by_id ID]])


(def TodoList
  [:map
   [:id ID]
   [:created_by_id ID]
   [:updated_by_id ID]])


(def schema
  {:user      {:prefix   :u
               :generate {:schema User}
               :fixtures {:table-name "users"}}
   :todo      {:generate  {:set    {:todo_title "write unit tests"}
                           :schema Todo}
               :fixtures  {:table-name "todos"}
               :relations {:created_by_id [:user :id]
                           :updated_by_id [:user :id]
                           :todo_list_id  [:todo-list :id]}
               :prefix    :t}
   :todo-list {:generate  {:schema TodoList}
               :fixtures  {:table-name "todo_lists"}
               :relations {:created_by_id [:user :id]
                           :updated_by_id [:user :id]}
               :prefix    :tl}})

(defn potato-db
  "returns an potato-db. a function so that it can deref test-dbspec"
  []
  {:schema   schema
   :generate {:generator mg/generate}
   :fixtures (merge dnj/config
                    {:dbspec @test-dbspec
                     :setup  (fn [_]
                               (create-tables dc/*connection*)
                               (reset! dgt/id-atom 0))})})

;;---
;; tests
;;---

(deftest inserts-simple-generated-data
  (dc/with-fixtures (potato-db)
    (testing (str (:dbtype dc/*connection*))
      (dc/insert-fixtures {:user [{:count 2}]})
      (is (match? [{:id 1 :username string?}
                   {:id 2 :username string?}]
                  (jdbc/query dc/*connection* ["SELECT * FROM users"]))))))

(deftest inserts-generated-data-hierarchy
  (dc/with-fixtures (potato-db)
    (testing (str (:dbtype dc/*connection*))
      (dc/insert-fixtures {:todo [{:count 2}]})
      (is (match? [{:id 1 :username string?}]
                  (jdbc/query dc/*connection* ["SELECT * FROM users"])))

      (is (match? [{:id            5,
                    :todo_list_id  2
                    :todo_title    "write unit tests"
                    :created_by_id 1
                    :updated_by_id 1}
                   {:id            8
                    :todo_list_id  2
                    :todo_title    "write unit tests"
                    :created_by_id 1
                    :updated_by_id 1}]
                  (jdbc/query dc/*connection* ["SELECT * FROM todos"])))

      (is (match? [{:id            2
                    :created_by_id 1
                    :updated_by_id 1}]
                  (jdbc/query dc/*connection* ["SELECT * FROM todo_lists"]))))))
