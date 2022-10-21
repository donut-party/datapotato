(ns donut.datapotato.datomic-test
  (:require
   [clojure.test :refer [deftest is testing]]
   [datomic.api :as d]
   [donut.datapotato.core :as dc]
   [donut.datapotato.datomic :as dd]
   [malli.generator :as mg]
   [matcher-combinators.test]))


;;---
;; schemas
;;---

(def User
  [:map
   [:user/username string?]])

(def TodoList
  [:map
   [:todo-list/created-by pos-int?]])

(def Todo
  [:map
   [:todo/todo-title string?]
   [:todo/created-by pos-int?]
   [:todo/todo-list pos-int?]])

(def Project
  [:map
   [:project/todo-lists
    [:vector pos-int?]]
   [:project/created-by pos-int?]])

(def schema
  {:user      {:prefix   :u
               :generate {:schema User}
               :fixtures {:collection :user}}
   :todo-list {:generate  {:schema TodoList}
               :fixtures  {:collection :todo-list}
               :relations {:todo-list/created-by [:user :db/id]}
               :prefix    :tl}
   :todo      {:generate  {:set {:todo/todo-title "write unit tests"}
                           :schema     Todo}
               :fixtures  {:collection :todo}
               :relations {:todo/created-by [:user :db/id]
                           :todo/todo-list  [:todo-list :db/id]}
               :prefix    :t}
   :project   {:generate    {:schema Project}
               :relations   {:project/created-by [:user :db/id]
                             :project/todo-lists [:todo-list :db/id]}
               :constraints {:project/todo-lists #{:coll}}
               :prefix      :p}})

(def uri "datomic:mem://datapotato-test")

(def potato-db
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
                      :db.install/_attribute :db.part/db}

                     {:db/ident              :project/todo-lists
                      :db/id                 #db/id [:db.part/db]
                      :db/valueType          :db.type/ref
                      :db/cardinality        :db.cardinality/many
                      :db.install/_attribute :db.part/db}

                     {:db/ident              :project/created-by
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
  (dc/with-fixtures potato-db
    (dc/insert-fixtures {:user [{:count 2}]})
    (is (match? [{:db/id 17592186045418 :user/username string?}
                 {:db/id 17592186045420 :user/username string?}]
                (q dc/*connection*
                   '{:find  [(pull ?u [*])]
                     :where [[?u :user/username]]})))))


(deftest inserts-generated-data-hierarchy
  (dc/with-fixtures potato-db
    (dc/insert-fixtures {:todo [{:count 2}]})
    (is (match? [{:db/id 17592186045418
                  :user/username string?}]
                (q dc/*connection*
                   '{:find  [(pull ?u [*])]
                     :where [[?u :user/username]]})))

    (is (match? [{:db/id           17592186045422
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

    (is (match? [{:db/id 17592186045420,
                  :todo-list/created-by #:db{:id 17592186045418}}]
                (q dc/*connection*
                   '{:find  [(pull ?u [*])]
                     :where [[?u :todo-list/created-by]]})))))

(deftest inserts-colls
  (dc/with-fixtures potato-db
    (dc/insert-fixtures {:project [{:refs {:project/todo-lists 2}}]})

    (is (match? [{:db/id         17592186045418
                  :user/username string?}]
                (q dc/*connection*
                   '{:find  [(pull ?u [*])]
                     :where [[?u :user/username]]})))

    (is (match? [{:db/id                17592186045420,
                  :todo-list/created-by #:db{:id 17592186045418}}
                 {:db/id                17592186045422,
                  :todo-list/created-by #:db{:id 17592186045418}}]
                (q dc/*connection*
                   '{:find  [(pull ?u [*])]
                     :where [[?u :todo-list/created-by]]})))

    (is (match? [{:db/id              17592186045424,
                  :project/todo-lists [{:db/id 17592186045420} {:db/id 17592186045422}]
                  :project/created-by {:db/id 17592186045418}}]
                (q dc/*connection*
                   '{:find  [(pull ?u [*])]
                     :where [[?u :project/created-by]]})))))

;;---
;; polymorphic test
;;---

(def TopicCategory
  [:map
   [:topic-category/name string?]])

(def Topic
  [:map
   [:topic/topic-category pos-int?]])

(def Watch
  [:map
   [:watch/watched pos-int?]])

(def polymorphic-schema
  {:topic-category {:generate {:schema TopicCategory
                               :set    {:topic-category/name "topic category"}}
                    :prefix   :tc}
   :topic          {:generate  {:schema Topic}
                    :relations {:topic/topic-category [:topic-category :db/id]}
                    :prefix    :t}
   :watch          {:generate  {:schema Watch}
                    :relations {:watch/watched #{[:topic-category :db/id]
                                                 [:topic :db/id]}}
                    :prefix    :w}})

(def polymorphic-potato-db
  {:schema   polymorphic-schema
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
                    [{:db/ident              :topic-category/name
                      :db/id                 #db/id [:db.part/db]
                      :db/valueType          :db.type/string
                      :db/cardinality        :db.cardinality/one
                      :db.install/_attribute :db.part/db}

                     {:db/ident              :topic/topic-category
                      :db/id                 #db/id [:db.part/db]
                      :db/valueType          :db.type/ref
                      :db/cardinality        :db.cardinality/one
                      :db.install/_attribute :db.part/db}

                     {:db/ident              :watch/watched
                      :db/id                 #db/id [:db.part/db]
                      :db/valueType          :db.type/ref
                      :db/cardinality        :db.cardinality/one
                      :db.install/_attribute :db.part/db}])))}})


(deftest polymorphic-refs
  (testing "incremental insert two different polymorphic refs"
    (dc/with-fixtures polymorphic-potato-db
      (let [updated-db (dc/insert-fixtures {:watch [{:count     1
                                                     :ref-types {:watch/watched :topic-category}}]})]

        (is (match? [{:db/id               17592186045418
                      :topic-category/name "topic category"}]
                    (q dc/*connection*
                       '{:find  [(pull ?e [*])]
                         :where [[?e :topic-category/name]]})))

        (is (match? [{:db/id         17592186045420
                      :watch/watched {:db/id 17592186045418}}]
                    (q dc/*connection*
                       '{:find  [(pull ?e [*])]
                         :where [[?e :watch/watched]]})))

        (dc/insert-fixtures updated-db {:watch [{:count     1
                                                 :ref-types {:watch/watched :topic}}]})

        (is (match? [{:db/id                17592186045422
                      :topic/topic-category {:db/id 17592186045418}}]
                    (q dc/*connection*
                       '{:find  [(pull ?e [*])]
                         :where [[?e :topic/topic-category]]})))

        (is (match? [{:db/id         17592186045420
                      :watch/watched {:db/id 17592186045418}}
                     {:db/id         17592186045424
                      :watch/watched {:db/id 17592186045422}}]
                    (q dc/*connection*
                       '{:find  [(pull ?e [*])]
                         :where [[?e :watch/watched]]}))))))

  (testing "single polymorphic ref"
    (dc/with-fixtures polymorphic-potato-db
      (dc/insert-fixtures {:watch [{:count     1
                                    :ref-types {:watch/watched :topic}}]})

      (is (match? [{:db/id               17592186045418
                    :topic-category/name "topic category"}]
                  (q dc/*connection*
                     '{:find  [(pull ?e [*])]
                       :where [[?e :topic-category/name]]})))

      (is (match? [{:db/id                17592186045420
                    :topic/topic-category {:db/id 17592186045418}}]
                  (q dc/*connection*
                     '{:find  [(pull ?e [*])]
                       :where [[?e :topic/topic-category]]})))

      (is (match? [{:db/id         17592186045422
                    :watch/watched {:db/id 17592186045420}}]
                  (q dc/*connection*
                     '{:find  [(pull ?e [*])]
                       :where [[?e :watch/watched]]}))))))
