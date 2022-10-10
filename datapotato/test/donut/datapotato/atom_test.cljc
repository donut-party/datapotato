(ns donut.datapotato.atom-test
  (:require
   [clojure.test :refer [deftest is]]
   [donut.datapotato.atom :as da]
   [donut.datapotato.core :as dc]
   [donut.datapotato.generate-test :as dgt]
   [malli.generator :as mg]
   [matcher-combinators.test])
  #?(:cljs (:require-macros [donut.datapotato.core])))

;;---
;; schemas
;;---

(def ID
  [:and {:gen/gen dgt/monotonic-id-gen} pos-int?])

(def User
  [:map
   [:users/id ID]
   [:users/username string?]])

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
   :fixtures {:insert da/insert
              :setup  (fn [_]
                        (reset! fixture-atom [])
                        (reset! dgt/id-seq 0))
              :atom   fixture-atom}})


;;---
;; tests
;;---

(deftest inserts-simple-generated-data
  (dc/with-fixtures ent-db
    (dc/insert-fixtures ent-db {:user [{:count 2}]})
    (is (match? [[:user #:users{:id 1 :username string?}]
                 [:user #:users{:id 2 :username string?}]]
                @fixture-atom))))


(deftest inserts-generated-data-hierarchy
  (dc/with-fixtures ent-db
    (dc/insert-fixtures ent-db {:todo [{:count 2}]})
    (is (match? [[:user #:users{:id 1 :username string?}]
                 [:todo-list #:todo_lists{:id            2
                                          :created_by_id 1
                                          :updated_by_id 1}]
                 [:todo #:todos{:id            5
                                :todo_title    "write unit tests"
                                :created_by_id 1
                                :updated_by_id 1
                                :todo_list_id  2}]
                 [:todo #:todos{:id            8
                                :todo_title    "write unit tests"
                                :created_by_id 1
                                :updated_by_id 1
                                :todo_list_id  2}]]
                @fixture-atom))))

(deftest overwrite-data-to-insert
  (dc/with-fixtures ent-db
    (dc/insert-fixtures ent-db {:todo [{:count    2
                                        :generate {:todos/todo_title "overwritten"}}]})
    (is (match? [[:user #:users{:id 1 :username string?}]
                 [:todo-list #:todo_lists{:id            2
                                          :created_by_id 1
                                          :updated_by_id 1}]
                 [:todo #:todos{:id            5
                                :todo_title    "overwritten"
                                :created_by_id 1
                                :updated_by_id 1
                                :todo_list_id  2}]
                 [:todo #:todos{:id            8
                                :todo_title    "overwritten"
                                :created_by_id 1
                                :updated_by_id 1
                                :todo_list_id  2}]]
                @fixture-atom))))

(deftest incremental-insert
  (dc/with-fixtures ent-db
    (-> (dc/insert-fixtures ent-db {:todo [{:count    1
                                            :generate {:todos/todo_title "step 1"}}]})
        (dc/insert-fixtures {:todo [{:count    1
                                     :generate {:todos/todo_title "step 2"}}]}))
    (is (match? [[:user #:users{:id 1 :username string?}]
                 [:todo-list #:todo_lists{:id            2
                                          :created_by_id 1
                                          :updated_by_id 1}]
                 [:todo #:todos{:id            5
                                :todo_title    "step 1"
                                :created_by_id 1
                                :updated_by_id 1
                                :todo_list_id  2}]
                 [:todo #:todos{:id            8
                                :todo_title    "step 2"
                                :created_by_id 1
                                :updated_by_id 1
                                :todo_list_id  2}]]
                @fixture-atom))))
