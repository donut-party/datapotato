(ns donut.datapotato.next-jdbc-example
  (:require
   [clojure.test :refer [deftest is]]
   [donut.datapotato.core :as dc]
   [donut.datapotato.next-jdbc :as dnj]
   [malli.generator :as mg]
   [matcher-combinators.test]
   [next.jdbc :as jdbc]
   [next.jdbc.sql :as sql]))

(def User
  [:map
   [:id pos-int?]
   [:username string?]])

(def DreamJournal
  [:map
   [:id pos-int?]
   [:owner-id pos-int?]
   [:dream-journal-name string?]])

(def Entry
  [:map
   [:id pos-int?]
   [:dream-journal-id pos-int?]
   [:content string?]])

(def potato-schema
  {:user          {:prefix   :u
                   :generate {:schema User}}
   :dream-journal {:prefix    :dj
                   :generate  {:schema DreamJournal}
                   :relations {:owner-id [:user :id]}}
   :entry         {:prefix    :e
                   :generate  {:schema Entry}
                   :relations {:dream-journal-id [:dream-journal :id]}}})

(defn create-tables
  [conn]
  (doseq [table-name ["users" "dream_journals" "entries"]]
    (try
      (jdbc/execute! conn [(str "DROP TABLE " table-name)])
      (catch Exception _)))
  (jdbc/execute!
   conn
   ["CREATE TABLE users (
       id integer PRIMARY KEY,
       username text NOT NULL
    )"])
  ;; create table for dream journal, entry
  )

(def potato-db
  {:schema   potato-schema
   :generate {:generator mg/generate}
   :fixtures (merge dnj/config
                    {:dbspec {:dbtype         "sqlite"
                              :connection-uri "jdbc:sqlite::memory:"}
                     :setup  (fn [_]
                               (create-tables dc/*connection*))})})


(deftest inserts-simple-generated-data
  (dc/with-fixtures potato-db
    (dc/insert-fixtures {:user [{:count 2}]})
    (is (match? [#:users{:id 1 :username string?}
                 #:users{:id 2 :username string?}]
                (sql/query dc/*connection* ["SELECT * FROM users"])))))
