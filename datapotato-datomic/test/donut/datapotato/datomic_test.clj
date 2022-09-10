(ns donut.datapotato.datomic-test
  (:require
   [clojure.test :refer [deftest is]]
   [datomic.api :as d]
   [donut.datapotato.core :as dc]
   [donut.datapotato.datomic :as dd]
   [malli.generator :as mg]))


(def node-atom (atom nil))

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
               :relations {:todo-list/created-by [:user :db/id]}
               :prefix    :tl}
   :todo      {:generate  {:overwrites {:todo/todo-title "write unit tests"}
                           :schema     Todo}
               :fixtures  {:collection :todo}
               :relations {:todo/created-by [:user :db/id]
                           :todo/todo-list  [:todo-list :db/id]}
               :prefix    :t}})

(def uri "datomic:mem://datapotato-test")

(def ent-db
  {:schema   schema
   :generate {:generator mg/generate}
   :fixtures {:insert
              dd/insert

              :get-connection
              (fn get-connection [_]
                (d/delete-database uri)
                (d/create-database uri)
                (d/connect uri))

              :setup
              (fn setup [{:keys [fixtures]}]
                (let [{:keys [connection]} fixtures]
                  @(d/transact
                    connection
                    [{:db/ident              :user/username
                      :db/id                 #db/id [:db.part/db]
                      :db/valueType          :db.type/string
                      :db/cardinality        :db.cardinality/one
                      :db.install/_attribute :db.part/db}

                     {:db/ident              :todo-list/created-by
                      :db/id                 #db/id [:db.part/db]
                      :db/valueType          :db.type/ref
                      :db/cardinality        :db.cardinality/one
                      :db.install/_attribute :db.part/db}

                     {:db/ident              :todo/todo-title
                      :db/id                 #db/id [:db.part/db]
                      :db/valueType          :db.type/string
                      :db/cardinality        :db.cardinality/one
                      :db.install/_attribute :db.part/db}

                     {:db/ident              :todo/created-by
                      :db/id                 #db/id [:db.part/db]
                      :db/valueType          :db.type/ref
                      :db/cardinality        :db.cardinality/one
                      :db.install/_attribute :db.part/db}

                     {:db/ident              :todo/todo-list
                      :db/id                 #db/id [:db.part/db]
                      :db/valueType          :db.type/ref
                      :db/cardinality        :db.cardinality/one
                      :db.install/_attribute :db.part/db}])))}})

;;---
;; tests
;;---

(defn q
  [connection query]
  (->> (d/q query (d/db connection))
       (map first)
       (sort-by :db/id)))

(deftest inserts-simple-generated-data
  (dc/with-fixtures ent-db
    (dc/insert-fixtures dc/*ent-db* {:user [{:count 2}]})
    (is (= [{:db/id 17592186045418 :user/username "Luigi"}
            {:db/id 17592186045420 :user/username "Luigi"}]
           (q dc/*connection*
              '{:find  [(pull ?u [*])]
                :where [[?u :user/username]]})))))


(deftest inserts-generated-data-hierarchy
  (dc/with-fixtures ent-db
    (dc/insert-fixtures dc/*ent-db* {:todo [{:count 2}]})
    (is (= [{:db/id 17592186045418
             :user/username "Luigi"}]
           (q dc/*connection*
              '{:find  [(pull ?u [*])]
                :where [[?u :user/username]]})))

    (is (= [{:db/id           17592186045422
             :todo/todo-title "write unit tests"
             :todo/created-by #:db{:id 17592186045418}
             :todo/todo-list  #:db{:id 17592186045420}}
            {:db/id           17592186045424
             :todo/todo-title "write unit tests"
             :todo/created-by #:db{:id 17592186045418}
             :todo/todo-list  #:db{:id 17592186045420}}]
           (q dc/*connection*
              '{:find  [(pull ?u [*])]
                :where [[?u :todo/todo-title]]})))

    (is (= [{:db/id 17592186045420,
             :todo-list/created-by #:db{:id 17592186045418}}]
           (q dc/*connection*
              '{:find  [(pull ?u [*])]
                :where [[?u :todo-list/created-by]]})))))
