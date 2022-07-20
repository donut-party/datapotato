(ns donut.datapotato.fixtures.atom-test
  (:require
   [clojure.test :refer [deftest is]]
   [donut.datapotato.core :as dc]
   [donut.datapotato.fixtures.atom :as dfa]
   [donut.datapotato.generate-test :as dgt]
   [malli.generator :as mg]))

;;---
;; schemas
;;---

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

(def fixture-atom (atom []))

(def ent-db
  {:schema   schema
   :generate {:generator mg/generate}
   :fixtures {:perform-insert dfa/perform-insert
              :setup          (fn [_] (reset! dgt/id-seq 0))
              :atom           fixture-atom}})


;;---
;; tests
;;---

(deftest inserts-simple-generated-data
  (dfa/with-fixtures ent-db
    (dc/insert-fixtures ent-db {:user [{:count 2}]})
    (is (= [[:user #:users{:id 1 :username "Luigi"}]
            [:user #:users{:id 2 :username "Luigi"}]]
           @fixture-atom))))
