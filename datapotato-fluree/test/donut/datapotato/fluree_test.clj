(ns donut.datapotato.fluree-test
  (:require
   [clojure.test :refer [deftest is]]
   [donut.datapotato.core :as dc]
   [donut.datapotato.generate-test :as dgt]
   [donut.datapotato.fluree :as df]
   [fluree.db.api :as fdb]
   [malli.generator :as mg]))

(def fdb-host "http://localhost:8090")
(defonce conn-atom (atom nil))

;;---
;; schemas
;;---

(def User
  [:map
   [:user/username [:enum "Luigi"]]])

(def TodoList
  [:map
   [:todo-list/created-by pos-int?]])

(def Todo
  [:map
   [:todo/todo-title string?]
   [:todo/created-by pos-int?]
   [:todo/todo-list pos-int?]])

(def schema
  {:user      {:prefix   :u
               :generate {:schema User}
               :fixtures {:collection :user}}
   :todo-list {:generate  {:schema TodoList}
               :fixtures  {:collection :todo-list}
               :relations {:todo-list/created-by [:user :_id]}
               :prefix    :tl}
   :todo      {:generate  {:overwrites {:todo/todo-title "write unit tests"}
                           :schema     Todo}
               :fixtures  {:collection :todo}
               :relations {:todo/created-by [:user :_id]
                           :todo/todo-list  [:todo-list :_id]}
               :prefix    :t}})

(defonce ledger-suffix (atom 0))
(defn ledger-name
  []
  (str "datapotato/test-ledger-" (swap! ledger-suffix inc)))

(def ent-db
  {:schema   schema
   :generate {:generator mg/generate}
   :fixtures {:perform-insert
              df/perform-insert

              :get-connection
              (fn open-connection [_]
                (let [conn            (or (:conn @conn-atom) (fdb/connect fdb-host))
                      new-ledger-name (ledger-name)]
                  @(fdb/new-ledger conn new-ledger-name)
                  (loop [remaining 3]
                    (when-not (or (zero? remaining)
                                  (= "ready" (:status @(fdb/ledger-info conn new-ledger-name))))
                      (Thread/sleep 500)
                      (recur (dec remaining))))
                  (reset! conn-atom {:conn   conn
                                     :ledger new-ledger-name}))
                @conn-atom)

              :setup
              (fn setup [{:keys [fixtures]}]
                (reset! dgt/id-seq 0)
                (let [{:keys [connection]}  fixtures
                      {:keys [conn ledger]} connection]
                  @(fdb/transact conn ledger [{:_id              :_collection
                                               :_collection/name :user}
                                              {:_id              :_collection
                                               :_collection/name :todo-list}
                                              {:_id              :_collection
                                               :_collection/name :todo}

                                              {:_id             :_predicate
                                               :_predicate/name :user/username
                                               :_predicate/type :string}

                                              {:_id             :_predicate
                                               :_predicate/name :todo-list/created-by
                                               :_predicate/type :ref}

                                              {:_id             :_predicate
                                               :_predicate/name :todo/todo-title
                                               :_predicate/type :string}
                                              {:_id             :_predicate
                                               :_predicate/name :todo/todo-list
                                               :_predicate/type :ref}
                                              {:_id             :_predicate
                                               :_predicate/name :todo/created-by
                                               :_predicate/type :ref}])))

              :teardown
              (fn teardown [_]
                (when @conn-atom
                  (let [{:keys [conn ledger]} @conn-atom]
                    @(fdb/delete-ledger conn ledger))))}})

(defn q
  [connection query]
  @(fdb/query (fdb/db (:conn connection) (:ledger connection))
              query))

#_
(deftest inserts-simple-generated-data
  (dc/with-fixtures ent-db
    (dc/insert-fixtures dc/*ent-db* {:user [{:count 2}]})
    (is (= [{:_id            351843720888321
             "user/username" "Luigi"}
            {:_id            351843720888320
             "user/username" "Luigi"}]
           (q dc/*connection*
              {:select [:*]
               :from "user"})))))

#_
(deftest inserts-generated-data-hierarchy
  (dc/with-fixtures ent-db
    (dc/insert-fixtures dc/*ent-db* {:todo [{:count 2}]})
    (is (= [{:_id            351843720888320
             "user/username" "Luigi"}]
           (q dc/*connection*
              {:select [:*]
               :from   "user"})))

    (is (= [{:_id              387028092977154
             "todo/todo-title" "write unit tests"
             "todo/created-by" {:_id 351843720888320}
             "todo/todo-list"  {:_id 387028092977152}}
            {:_id              387028092977153
             "todo/todo-title" "write unit tests"
             "todo/created-by" {:_id 351843720888320}
             "todo/todo-list"  {:_id 387028092977152}}]
           (q dc/*connection*
              {:select [:*]
               :from   "todo"})))

    (is (= [{:_id                   387028092977152
             "todo-list/created-by" {:_id 351843720888320}}]
           (q dc/*connection*
              {:select [:*]
               :from   "todo-list"})))))
