(ns donut.datapotato.insert.next-jdbc-test
  {:clj-kondo/config
   '{:linters
     {:unresolved-symbol
      {:exclude [(donut.datapotato.insert.next-jdbc-test/with-conn [conn])]}}}}
  (:require
   [clojure.test :refer [deftest is]]
   [donut.datapotato.generate-test :as ddgt]
   [donut.datapotato.insert.next-jdbc :as ddin]
   [malli.generator :as mg]
   [next.jdbc :as jdbc]
   [next.jdbc.sql :as sql]))

(def db-spec
  {:dbtype         "sqlite"
   :connection-uri "jdbc:sqlite::memory:"})

(def ID
  [:and {:gen/gen ddgt/monotonic-id-gen} pos-int?])

(def User
  [:map
   [:id ID]
   [:username [:enum "Luigi"]]])

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
  {:user            {:prefix   :u
                     :generate {:schema User}
                     :insert   {:table-name "users"}}
   :todo            {:generate  {:overwrites {:todo_title "write unit tests"}
                                 :schema     Todo}
                     :insert    {:table-name "todos"}
                     :relations {:created_by_id [:user :id]
                                 :updated_by_id [:user :id]
                                 :todo_list_id  [:todo-list :id]}
                     :prefix    :t}
   :todo-list       {:generate  {:schema TodoList}
                     :insert    {:table-name "todo_lists"}
                     :relations {:created_by_id [:user :id]
                                 :updated_by_id [:user :id]}
                     :prefix    :tl}})

(defn create-tables
  [conn]
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
       todo_title test NOT NULL,
       created_by_id INTEGER,
       updated_by_id INTEGER
    )"])
  )

(defmacro with-conn
  [conn-name & body]
  `(with-open [~conn-name (jdbc/get-connection db-spec)]
     (create-tables ~conn-name)
     (reset! ddgt/id-seq 0)
     ~@body))

(deftest inserts-simple-generated-data
  (with-conn conn
    (ddin/generate-insert {:schema   schema
                           :generate {:generator mg/generate}
                           :insert   {:get-insert-db (constantly conn)}}
                          {:user [[2]]})
    (is (= [#:users{:id 1 :username "Luigi"}
            #:users{:id 2 :username "Luigi"}]
           (sql/query conn ["SELECT * FROM users"])))))

(deftest inserts-generated-data-hierarchy
  (with-conn conn
    (ddin/generate-insert
     {:schema   schema
      :generate {:generator mg/generate}
      :insert   {:get-insert-db (constantly conn)
                 :get-inserted  (fn [{:keys [db table-name insert-result]}]
                                  (first (sql/query db [(str "SELECT * FROM " table-name
                                                             "  WHERE rowid = ?")
                                                        (-> insert-result vals first)])))}}
     {:todo [[2]]})

    (is (= [#:users{:id 1 :username "Luigi"}]
           (sql/query conn ["SELECT * FROM users"])))

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
           (sql/query conn ["SELECT * FROM todos"])))

    (is (= [#:todo_lists{:id            2
                         :created_by_id 1
                         :updated_by_id 1}]
           (sql/query conn ["SELECT * FROM todo_lists"])))))
