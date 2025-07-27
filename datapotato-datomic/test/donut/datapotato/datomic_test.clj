(ns donut.datapotato.datomic-test
  (:require
   [clojure.test :refer [deftest is testing]]
   [datomic.client.api :as d]
   [donut.datapotato.core :as dc]
   [donut.datapotato.datomic :as dd]
   [malli.generator :as mg]
   [matcher-combinators.matchers :as mcm]
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

(def client (d/client {:server-type :datomic-local
                       :storage-dir :mem
                       :system      "dev"}))

(def db-arg-map {:db-name "datapotato-test"})

(defn get-connection [_]
  (d/delete-database client db-arg-map)
  (d/create-database client db-arg-map)
  (d/connect client db-arg-map))

(def potato-db
  {:schema   schema
   :generate {:generator mg/generate}
   :fixtures {:insert
              dd/insert

              :get-connection
              get-connection

              :setup
              (fn setup [_]
                (d/transact dc/*connection* {:tx-data
                                             [{:db/ident       :user/username
                                               :db/valueType   :db.type/string
                                               :db/cardinality :db.cardinality/one}

                                              {:db/ident       :todo-list/created-by
                                               :db/valueType   :db.type/ref
                                               :db/cardinality :db.cardinality/one}

                                              {:db/ident       :todo/todo-title
                                               :db/valueType   :db.type/string
                                               :db/cardinality :db.cardinality/one}

                                              {:db/ident       :todo/created-by
                                               :db/valueType   :db.type/ref
                                               :db/cardinality :db.cardinality/one}

                                              {:db/ident       :todo/todo-list
                                               :db/valueType   :db.type/ref
                                               :db/cardinality :db.cardinality/one}

                                              {:db/ident       :project/todo-lists
                                               :db/valueType   :db.type/ref
                                               :db/cardinality :db.cardinality/many}

                                              {:db/ident       :project/created-by
                                               :db/valueType   :db.type/ref
                                               :db/cardinality :db.cardinality/one}]}))}})

(defn expected-ref [m] (select-keys m [:db/id]))

;;---
;; tests
;;---

(defn q
  "lil helper helpin out"
  [connection query]
  (->> (d/q query (d/db connection))
       (map first)
       (sort-by :db/id)))

(deftest inserts-simple-generated-data
  (dc/with-fixtures potato-db
    (let [{:keys [u0 u1]} (dc/insert-fixtures {:user [{:count 2}]})]
      (is (match? [{:db/id pos-int? :user/username string?}
                   {:db/id pos-int? :user/username string?}]
                  [u0 u1]))
      (is (= #{u0 u1}
             (set (q dc/*connection*
                     '{:find  [(pull ?u [*])]
                       :where [[?u :user/username]]})))))))


(deftest inserts-generated-data-hierarchy
  (dc/with-fixtures potato-db
    (let [{:keys [u0 tl0]} (dc/insert-fixtures {:todo [{:count 2}]})
          expected-u       (expected-ref u0)
          expected-tl      (expected-ref tl0)]
      (is (match? [{:db/id         pos-int?
                    :user/username string?}]
                  (q dc/*connection*
                     '{:find  [(pull ?u [*])]
                       :where [[?u :user/username]]})))

      (is (match? [{:db/id           pos-int?
                    :todo/todo-title "write unit tests"
                    :todo/created-by expected-u
                    :todo/todo-list  expected-tl}
                   {:db/id           pos-int?
                    :todo/todo-title "write unit tests"
                    :todo/created-by expected-u
                    :todo/todo-list  expected-tl}]
                  (q dc/*connection*
                     '{:find  [(pull ?u [*])]
                       :where [[?u :todo/todo-title]]})))

      (is (match? [{:db/id                pos-int?
                    :todo-list/created-by expected-u}]
                  (q dc/*connection*
                     '{:find  [(pull ?u [*])]
                       :where [[?u :todo-list/created-by]]}))))))

(deftest inserts-colls
  (dc/with-fixtures potato-db
    (let [{:keys [u0 tl0 tl1]} (dc/insert-fixtures {:project [{:refs {:project/todo-lists 2}}]})
          expected-u           (expected-ref u0)
          expected-tl0         (expected-ref tl0)
          expected-tl1         (expected-ref tl1)]

      (is (= [u0]
             (q dc/*connection*
                '{:find  [(pull ?u [*])]
                  :where [[?u :user/username]]})))

      (is (= #{tl0 tl1}
             (set (q dc/*connection*
                     '{:find  [(pull ?u [*])]
                       :where [[?u :todo-list/created-by]]}))))

      (is (match? [{:db/id              pos-int?
                    :project/todo-lists (mcm/in-any-order [expected-tl0 expected-tl1])
                    :project/created-by expected-u}]
                  (q dc/*connection*
                     '{:find  [(pull ?u [*])]
                       :where [[?u :project/created-by]]}))))))

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
              get-connection

              :setup
              (fn setup [{:keys [fixtures]}]
                (let [{:keys [connection]} fixtures]
                  (d/transact connection {:tx-data [{:db/ident       :topic-category/name
                                                     :db/valueType   :db.type/string
                                                     :db/cardinality :db.cardinality/one}

                                                    {:db/ident       :topic/topic-category
                                                     :db/valueType   :db.type/ref
                                                     :db/cardinality :db.cardinality/one}

                                                    {:db/ident       :watch/watched
                                                     :db/valueType   :db.type/ref
                                                     :db/cardinality :db.cardinality/one}]})))}})


(deftest polymorphic-refs
  (testing "incremental insert two different polymorphic refs"
    (dc/with-fixtures polymorphic-potato-db
      (let [{:keys [w0 tc0] :as updated-db} (dc/insert-fixtures {:watch [{:count     1
                                                                          :ref-types {:watch/watched :topic-category}}]})]

        (is (match? [tc0]
                    (q dc/*connection*
                       '{:find  [(pull ?e [*])]
                         :where [[?e :topic-category/name]]})))

        (is (match? [w0]
                    (q dc/*connection*
                       '{:find  [(pull ?e [*])]
                         :where [[?e :watch/watched]]})))

        (let [{:keys [w1 t0]} (dc/insert-fixtures updated-db {:watch [{:count     1
                                                                       :ref-types {:watch/watched :topic}}]})]

          (is (match? [t0]
                      (q dc/*connection*
                         '{:find  [(pull ?e [*])]
                           :where [[?e :topic/topic-category]]})))

          (is (= (set [w0 w1])
                 (set (q dc/*connection*
                         '{:find  [(pull ?e [*])]
                           :where [[?e :watch/watched]]}))))))))

  (testing "single polymorphic ref"
    (dc/with-fixtures polymorphic-potato-db
      (let [{:keys [t0 tc0 w0]} (dc/insert-fixtures {:watch [{:count     1
                                                              :ref-types {:watch/watched :topic}}]})]

        (is (= [tc0]
               (q dc/*connection*
                  '{:find  [(pull ?e [*])]
                    :where [[?e :topic-category/name]]})))

        (is (= [t0]
               (q dc/*connection*
                  '{:find  [(pull ?e [*])]
                    :where [[?e :topic/topic-category]]})))

        (is (= [w0]
               (q dc/*connection*
                  '{:find  [(pull ?e [*])]
                    :where [[?e :watch/watched]]})))))))
