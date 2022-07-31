(ns donut.datapotato.xtdb-test
  (:require
   [clojure.test :refer [deftest is]]
   [donut.datapotato.core :as dc]
   [donut.datapotato.generate-test :as dgt]
   [donut.datapotato.xtdb :as dxtdb]
   [malli.generator :as mg]
   [xtdb.api :as xt]))

(def node-atom (atom nil))

(reset! node-atom (xt/start-node {}))

;;---
;; schemas
;;---

(def ID
  [:and {:gen/gen dgt/monotonic-id-gen} pos-int?])

(def User
  [:map
   [:xt/id ID]
   [:user/username [:enum "Luigi"]]])

(def Todo
  [:map
   [:xt/id ID]
   [:todo/todo-title string?]
   [:todo/created-by ID]
   [:todo/updated-by ID]])


(def TodoList
  [:map
   [:xt/id ID]
   [:todo-list/created-by ID]
   [:todo-list/updated-by ID]])


(def schema
  {:user      {:prefix   :u
               :generate {:schema User}
               :fixtures {:table-name "users"}}
   :todo      {:generate  {:overwrites {:todo/todo-title "write unit tests"}
                           :schema     Todo}
               :fixtures  {:table-name "todos"}
               :relations {:todo/created-by [:user :xt/id]
                           :todo/updated-by [:user :xt/id]
                           :todo/todo-list  [:todo-list :xt/id]}
               :prefix    :t}
   :todo-list {:generate  {:schema TodoList}
               :fixtures  {:table-name "todo-lists"}
               :relations {:todo-list/created-by [:user :xt/id]
                           :todo-list/updated-by [:user :xt/id]}
               :prefix    :tl}})

(def fixture-atom (atom []))

(def ent-db
  {:schema   schema
   :generate {:generator mg/generate}
   :fixtures {:perform-insert dxtdb/perform-insert
              :get-connection (fn get-connection [_]
                                (when-let [node @node-atom]
                                  (.close node))
                                (reset! node-atom (xt/start-node {}))
                                @node-atom)
              :setup          (fn setup [_] (reset! dgt/id-seq 0))
              :atom           fixture-atom}})

(defn q
  [query]
  (->> (xt/q (xt/db @node-atom) query)
       (map first)
       (sort-by :xt/id)))

(deftest inserts-simple-generated-data
  (dc/with-fixtures ent-db
    (dc/insert-fixtures dc/*ent-db* {:user [{:count 2}]})
    (is (= [{:xt/id 1 :user/username "Luigi"}
            {:xt/id 2 :user/username "Luigi"}]
           (q '{:find  [(pull ?u [*])]
                :where [[?u :user/username]]})))))


(deftest inserts-generated-data-hierarchy
  (dc/with-fixtures ent-db
    (dc/insert-fixtures dc/*ent-db* {:todo [{:count 2}]})
    (is (= [{:xt/id 1 :user/username "Luigi"}]
           (q '{:find  [(pull ?u [*])]
                :where [[?u :user/username]]})))

    (is (= [{:xt/id            5,
             :todo/todo-list  2
             :todo/todo-title    "write unit tests"
             :todo/created-by 1
             :todo/updated-by 1}
            {:xt/id            8
             :todo/todo-list  2
             :todo/todo-title    "write unit tests"
             :todo/created-by 1
             :todo/updated-by 1}]
           (q '{:find  [(pull ?u [*])]
                :where [[?u :todo/todo-title]]})))

    (is (= [{:xt/id            2
             :todo-list/created-by 1
             :todo-list/updated-by 1}]
           (q '{:find  [(pull ?u [*])]
                :where [[?u :todo-list/created-by]]})))))
