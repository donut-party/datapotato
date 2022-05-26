(ns donut.datapotato.generate-test
  #?(:cljs (:require-macros [clojure.template :as ct]))
  (:require
   [clojure.data :as data]
   [clojure.spec.alpha :as s]
   [clojure.spec.gen.alpha :as sg]
   #?(:clj [clojure.template :as ct])
   [clojure.test.check.generators :as gen :include-macros true]
   #?(:clj [clojure.test :refer [deftest is are use-fixtures testing]]
      :cljs [cljs.test :include-macros true :refer [deftest is are use-fixtures testing]])
   [donut.datapotato.core :as dd]
   [malli.generator :as mg]))



;;---
;; test helpers
;;---

(def id-seq (atom 0))
(def monotonic-id-gen
  (gen/fmap (fn [_] (swap! id-seq inc)) (gen/return nil)))

(defn submap?
  "All vals in m1 are present in m2"
  [m1 m2]
  (nil? (first (data/diff m1 m2))))

(def gen-data-db (atom []))
(def gen-data-cycle-db (atom []))

(defn reset-dbs* []
  (reset! gen-data-db [])
  (reset! gen-data-cycle-db [])
  )

(defn reset-dbs [f]
  (reset-dbs*)
  (f))

(use-fixtures :each reset-dbs)

(defn ids-present?
  [generated]
  (every? pos-int? (map :id (vals generated))))

(defn only-has-ents?
  [generated ent-names]
  (= (set (keys generated))
     (set ent-names)))

(defn ids-match?
  "Reference attr vals equal their referent"
  [generated matches]
  (every? (fn [[ent id-path-map]]
            (every? (fn [[attr id-path-or-paths]]
                      (if (vector? (first id-path-or-paths))
                        (= (set (map (fn [id-path] (get-in generated id-path)) id-path-or-paths))
                           (set (get-in generated [ent attr])))
                        (= (get-in generated id-path-or-paths)
                           (get-in generated [ent attr]))))
                    id-path-map))
          matches))

;;---
;; base schema
;;---

(def schema
  {:user            {:prefix :u}
   :todo            {:generate  {:overwrites {:todo-title "write unit tests"}}
                     :relations {:created-by-id [:user :id]
                                 :updated-by-id [:user :id]
                                 :todo-list-id  [:todo-list :id]}
                     :prefix    :t}
   :todo-list       {:relations {:created-by-id [:user :id]
                                 :updated-by-id [:user :id]}
                     :prefix    :tl}
   :todo-list-watch {:relations   {:todo-list-id [:todo-list :id]
                                   :watcher-id   [:user :id]}
                     :constraints {:todo-list-id #{:uniq}}
                     :prefix      :tlw}
   :project         {:relations   {:created-by-id [:user :id]
                                   :updated-by-id [:user :id]
                                   :todo-list-ids [:todo-list :id]}
                     :constraints {:todo-list-ids #{:coll}}
                     :prefix      :p}})

(def cycle-schema
  {:user      {:prefix    :u
               :relations {:updated-by-id [:user :id]}}
   :todo      {:generate    {:overwrites {:todo-title "write unit tests"}}
               :relations   {:todo-list-id [:todo-list :id]}
               :constraints {:todo-list-id #{:required}}
               :prefix      :t}
   :todo-list {:relations {:first-todo-id [:todo :id]}
               :prefix    :tl}})

(def polymorphic-schema
  {:topic-category {:prefix :tc}
   :topic          {:relations {:topic-category-id [:topic-category :id]}
                    :prefix    :t}
   :watch          {:relations {:watched-id #{[:topic-category :id]
                                              [:topic :id]}}
                    :prefix    :w}})

;;---
;; spec
;;---

(s/def ::id (s/with-gen pos-int?
              (constantly monotonic-id-gen)))

(s/def ::username #{"Luigi"})
(s/def ::user (s/keys :req-un [::id ::username]))

(s/def ::created-by-id ::id)
(s/def ::updated-by-id ::id)

(s/def ::todo-title string?)
(s/def ::todo (s/keys :req-un [::id ::todo-title ::created-by-id ::updated-by-id]))

(s/def ::todo-id ::id)
(s/def ::attachment (s/keys :req-un [::id ::todo-id ::created-by-id ::updated-by-id]))

(s/def ::todo-list (s/keys :req-un [::id ::created-by-id ::updated-by-id]))

(s/def ::todo-list-id ::id)
(s/def ::watcher-id ::id)
(s/def ::todo-list-watch (s/keys :req-un [::id ::todo-list-id ::watcher-id]))

;; In THE REAL WORLD todo-list would probably have a project-id,
;; rather than project having some coll of :todo-list-ids
(s/def ::todo-list-ids (s/coll-of ::todo-list-id))
(s/def ::project (s/keys :req-un [::id ::todo-list-ids ::created-by-id ::updated-by-id]))

(def spec-schema
  (-> schema
      (assoc-in [:user :generate :schema] ::user)
      (assoc-in [:todo :generate :schema] ::todo)
      (assoc-in [:todo-list :generate :schema] ::todo-list)
      (assoc-in [:todo-list-watch :generate :schema] ::todo-list-watch)
      (assoc-in [:project :generate :schema] ::project)))


(def spec-cycle-schema
  (-> cycle-schema
      (assoc-in [:user :generate :schema] ::user)
      (assoc-in [:todo :generate :schema] ::user)
      (assoc-in [:todo-list :generate :schema] ::todo-list)))

(s/def ::topic-category (s/keys :req-un [::id]))

(s/def ::topic-category-id ::id)
(s/def ::topic (s/keys :req-un [::id ::topic-category-id]))

(s/def ::watched-id ::id)
(s/def ::watch (s/keys :req-un [::id ::watched-id]))

(def spec-polymorphic-schema
  (-> polymorphic-schema
      (assoc-in [:topic-category :generate :schema] ::topic-category)
      (assoc-in [:topic :generate :schema] ::todo)
      (assoc-in [:watch :generate :schema] ::watch)))

(def spec-generator (comp sg/generate s/gen))
(def malli-generator mg/generate)

;;---
;; malli
;;---

(def ID
  [:and {:gen/gen monotonic-id-gen} pos-int?])

(def User
  [:map
   [:id ID]
   [:username [:enum "Luigi"]]])

(def Todo
  [:map
   [:id ID]
   [:todo-title string?]
   [:created-by-id ID]
   [:updated-by-id ID]])


(def TodoList
  [:map
   [:id ID]
   [:created-by-id ID]
   [:updated-by-id ID]])

(def TodoListWatch
  [:map
   [:id ID]
   [:todo-list-id ID]
   [:watcher-id ID]])

;; In THE REAL WORLD todo-list would probably have a project-id,
;; rather than project having some coll of :todo-list-ids
(def Project
  [:map
   [:id ID]
   [:todo-list-ids [:vector ID]]
   [:created-by-id ID]
   [:updated-by-id ID]])

(def malli-schema
  (-> schema
      (assoc-in [:user :generate :schema] User)
      (assoc-in [:todo :generate :schema] Todo)
      (assoc-in [:todo-list :generate :schema] TodoList)
      (assoc-in [:todo-list-watch :generate :schema] TodoListWatch)
      (assoc-in [:project :generate :schema] Project)))

(def malli-cycle-schema
  (-> cycle-schema
      (assoc-in [:user :generate :schema] User)
      (assoc-in [:todo :generate :schema] Todo)
      (assoc-in [:todo-list :generate :schema] TodoList)))

(def TopicCategory [:map [:id ID]])
(def Topic
  [:map
   [:id ID]
   [:topic-category-id ID]])

(def Watch
  [:map
   [:id ID]
   [:watched-id ID]])

(def malli-polymorphic-schema
  (-> polymorphic-schema
      (assoc-in [:topic-category-id :generate :schema] TopicCategory)
      (assoc-in [:topic :generate :schema] Todo)
      (assoc-in [:watch :generate :schema] Watch)))

;;---
;; tests
;;---

(deftest test-generate
  (ct/do-template
   [generator-name ent-db]
   (testing generator-name
     (let [gen (dd/generate-attr-map
                ent-db
                {:todo-list [[1]]})]
       (is (submap? {:u0 {:username "Luigi"}}
                    gen))
       (is (ids-present? gen))
       (is (ids-match? gen
                       {:tl0 {:created-by-id [:u0 :id]
                              :updated-by-id [:u0 :id]}}))
       (is (only-has-ents? gen #{:tl0 :u0}))))

   "spec"
   {:schema    spec-schema
    :generate {:generator spec-generator}}

   "malli"
   {:schema    malli-schema
    :generate {:generator malli-generator}}))

(deftest test-spec-gen-nested
  (ct/do-template
   [generator-name ent-db]
   (testing generator-name
     (let [gen (dd/generate-attr-map
                ent-db
                {:project [[:_ {:refs {:todo-list-ids 3}}]]})]
       (is (submap? {:u0 {:username "Luigi"}} gen))
       (is (ids-present? gen))
       (is (ids-match? gen
                       {:tl0 {:created-by-id [:u0 :id]
                              :updated-by-id [:u0 :id]}
                        :tl1 {:created-by-id [:u0 :id]
                              :updated-by-id [:u0 :id]}
                        :tl2 {:created-by-id [:u0 :id]
                              :updated-by-id [:u0 :id]}
                        :p0  {:created-by-id [:u0 :id]
                              :updated-by-id [:u0 :id]
                              :todo-list-ids [[:tl0 :id]
                                              [:tl1 :id]
                                              [:tl2 :id]]}}))
       (is (only-has-ents? gen #{:tl0 :tl1 :tl2 :u0 :p0}))))

   "spec"
   {:schema   spec-schema
    :generate {:generator spec-generator}}

   "malli"
   {:schema   malli-schema
    :generate {:generator malli-generator}}))

(deftest test-spec-gen-manual-attr
  (ct/do-template
   [generator-name ent-db]
   (testing generator-name
     (testing "Manual attribute setting for non-reference field"
       (let [gen (dd/generate-attr-map
                  ent-db
                  {:todo [[:_ {:generate {:todo-title "pet the dog"}}]]})]
         (is (submap? {:u0 {:username "Luigi"}
                       :t0 {:todo-title "pet the dog"}}
                      gen))
         (is (ids-present? gen))
         (is (ids-match? gen
                         {:tl0 {:created-by-id [:u0 :id]
                                :updated-by-id [:u0 :id]}
                          :t0  {:created-by-id [:u0 :id]
                                :updated-by-id [:u0 :id]
                                :todo-list-id  [:tl0 :id]}}))
         (is (only-has-ents? gen #{:tl0 :t0 :u0}))))

     (testing "Manual attribute setting for reference field"
       (let [gen (dd/generate-attr-map
                  ent-db
                  {:todo [[:_ {:generate {:created-by-id 1}}]]})]
         (is (submap? {:u0 {:username "Luigi"}
                       :t0 {:created-by-id 1}}
                      gen))
         (is (ids-present? gen))
         (is (ids-match? gen
                         {:tl0 {:created-by-id [:u0 :id]
                                :updated-by-id [:u0 :id]}
                          :t0  {:updated-by-id [:u0 :id]
                                :todo-list-id  [:tl0 :id]}}))
         (is (only-has-ents? gen #{:tl0 :t0 :u0})))))

   "spec"
   {:schema   spec-schema
    :generate {:generator spec-generator}}

   "malli"
   {:schema   malli-schema
    :generate {:generator malli-generator}}
   ))

(deftest test-spec-gen-omit
  (ct/do-template
   [generator-name ent-db]
   (testing generator-name
     (testing "Ref not created and attr is not present when omitted"
       (let [gen (dd/generate-attr-map
                  ent-db
                  {:todo-list [[:_ {:refs {:created-by-id ::dd/omit
                                           :updated-by-id ::dd/omit}}]]})]
         (is (ids-present? gen))
         (is (only-has-ents? gen #{:tl0}))
         (is (= [:id] (keys (:tl0 gen))))))

     (testing "Ref is created when at least 1 field references it, but omitted attrs are still not present"
       (let [gen (dd/generate-attr-map
                  ent-db
                  {:todo-list [[:_ {:refs {:updated-by-id ::dd/omit}}]]})]
         (is (submap? {:u0 {:username "Luigi"}} gen))
         (is (ids-present? gen))
         (is (ids-match? gen
                         {:tl0 {:created-by-id [:u0 :id]}}))
         (is (only-has-ents? gen #{:tl0 :u0}))
         (is (= [:id :created-by-id] (keys (:tl0 gen))))))

     (testing "Overwriting value of omitted ref with custom value"
       (let [gen (dd/generate-attr-map
                  ent-db
                  {:todo-list [[:_ {:refs     {:updated-by-id ::dd/omit}
                                    :generate {:updated-by-id 42}}]]})]
         (is (ids-present? gen))
         (is (= 42 (-> gen :tl0 :updated-by-id)))))

     (testing "Overwriting value of omitted ref with nil"
       (let [gen (dd/generate-attr-map
                  ent-db
                  {:todo-list [[:_ {:refs     {:updated-by-id ::dd/omit}
                                    :generate {:updated-by-id nil}}]]})]
         (is (ids-present? gen))
         (is (= nil (-> gen :tl0 :updated-by-id))))))

   "spec"
   {:schema   spec-schema
    :generate {:generator spec-generator}}

   "malli"
   {:schema   malli-schema
    :generate {:generator malli-generator}}

   ))

(deftest test-overwriting
  (ct/do-template
   [generator-name ent-db]
   (testing generator-name
     (testing "Overwriting generated value with query map"
       (let [gen (dd/generate-attr-map
                  ent-db
                  {:todo-list [[:_ {:generate {:updated-by-id 42}}]]})]
         (is (ids-present? gen))
         (is (= 42 (-> gen :tl0 :updated-by-id)))))

     (testing "Overwriting generated value with query fn"
       (let [gen (dd/generate-attr-map
                  ent-db
                  {:todo-list [[:_ {:generate #(assoc % :updated-by-id :foo)}]]})]
         (is (ids-present? gen))
         (is (= :foo (-> gen :tl0 :updated-by-id)))))

     (testing "Overwriting generated value with schema map"
       (let [gen (dd/generate-attr-map
                  (assoc-in ent-db [:schema :todo :generate :overwrites :todo-title] "schema title")
                  {:todo [[:_ {:generate #(assoc % :updated-by-id :foo)}]]})]
         (is (ids-present? gen))
         (is (= "schema title" (-> gen :t0 :todo-title)))))

     (testing "Overwriting generated value with schema fn"
       (let [gen (dd/generate-attr-map
                  (assoc-in ent-db [:schema :todo :generate :overwrites] #(assoc % :todo-title "boop whooop"))
                  {:todo [[:_ {:generate #(assoc % :updated-by-id :foo)}]]})]
         (is (ids-present? gen))
         (is (= "boop whooop" (-> gen :t0 :todo-title))))))

   "spec"
   {:schema   spec-schema
    :generate {:generator spec-generator}}

   "malli"
   {:schema   malli-schema
    :generate {:generator malli-generator}}
   ))

(deftest test-idempotency
  (ct/do-template
   [generator-name ent-db]
   (testing generator-name
     (testing "Gen traversal won't replace already generated data with newly generated data"
       (let [gen-fn     #(dd/generate % {:todo [[:t0 {:generate {:todo-title "pet the dog"}}]]})
             first-pass (gen-fn ent-db)]
         (is (= (:data first-pass)
                (:data (gen-fn first-pass)))))))

   "spec"
   {:schema   spec-schema
    :generate {:generator spec-generator}}

   "malli"
   {:schema   malli-schema
    :generate {:generator malli-generator}}
   ))


(deftest test-coll-relval-order
  (ct/do-template
   [generator-name ent-db]
   (testing "When a relation has a `:coll` constraint, order its vals correctly"
     (let [gen (dd/generate-attr-map
                ent-db
                {:project [[:_ {:refs {:todo-list-ids 3}}]]})]
       (is (submap? {:u0 {:username "Luigi"}} gen))
       (is (ids-present? gen))
       (is (= (:todo-list-ids (:p0 gen))
              [(:id (:tl0 gen))
               (:id (:tl1 gen))
               (:id (:tl2 gen))]))
       (is (only-has-ents? gen #{:tl0 :tl1 :tl2 :u0 :p0}))))

   "spec"
   {:schema   spec-schema
    :generate {:generator spec-generator}}

   "malli"
   {:schema   malli-schema
    :generate {:generator malli-generator}}
   ))

(deftest test-sets-custom-relation-val
  (ct/do-template
   [generator-name ent-db]
   (testing generator-name
     (let [gen (dd/generate-attr-map
                ent-db
                {:user      [[:custom-user {:generate {:id 100}}]]
                 :todo-list [[:custom-tl {:refs {:created-by-id :custom-user
                                                 :updated-by-id :custom-user}}]]})]
       (is (submap? {:custom-user {:username "Luigi"
                                   :id       100}}
                    gen))
       (is (ids-present? gen))
       (is (ids-match? gen
                       {:custom-tl {:created-by-id [:custom-user :id]
                                    :updated-by-id [:custom-user :id]}}))
       (is (only-has-ents? gen #{:custom-tl :custom-user}))))

   "spec"
   {:schema   spec-schema
    :generate {:generator spec-generator}}

   "malli"
   {:schema   malli-schema
    :generate {:generator malli-generator}}
   ))

;; testing inserting
(defn insert
  [_db {:keys [ent-name attrs]}]
  (swap! gen-data-db conj [(:ent-type attrs) ent-name (:generate attrs)]))

(deftest test-insert-gen-data
  (ct/do-template
   [generator-name ent-db]
   (testing generator-name
     (reset-dbs*)
     (-> (dd/generate ent-db {:todo [[1]]})
         (dd/visit-ents-once :inserted-data insert))

     ;; gen data is something like:
     ;; [[:user :u0 {:id 1 :username "Luigi"}]
     ;;  [:todo-list :tl0 {:id 2 :created-by-id 1 :updated-by-id 1}]
     ;;  [:todo :t0 {:id            5
     ;;              :todo-title    "write unit tests"
     ;;              :created-by-id 1
     ;;              :updated-by-id 1
     ;;              :todo-list-id  2}]]

     (let [gen-data @gen-data-db]
       (is (= #{[:user :u0]
                [:todo-list :tl0]
                [:todo :t0]}
              (set (map #(take 2 %) gen-data))))

       (let [ent-map (into {} (map #(vec (drop 1 %)) gen-data))]
         (is (submap? {:u0 {:username "Luigi"}
                       :t0 {:todo-title "write unit tests"}}
                      ent-map))
         (is (ids-present? ent-map))
         (is (ids-match? ent-map
                         {:tl0 {:created-by-id [:u0 :id]
                                :updated-by-id [:u0 :id]}
                          :t0  {:created-by-id [:u0 :id]
                                :updated-by-id [:u0 :id]
                                :todo-list-id  [:tl0 :id]}})))))
   "spec"
   {:schema   spec-schema
    :generate {:generator spec-generator}}

   "malli"
   {:schema   malli-schema
    :generate {:generator malli-generator}}
   ))

(deftest test-inserts-novel-data
  (ct/do-template
   [generator-name ent-db]
   (testing generator-name
     (testing "Given a db with a todo already added, next call adds a new
  todo that references the same todo list and user"
       (let [db1 (-> (dd/generate ent-db {:todo [[1]]})
                     (dd/visit-ents-once :inserted-data insert))]
         (-> (dd/generate db1 {:todo [[1]]})
             (dd/visit-ents-once :inserted-data insert))

         (let [gen-data @gen-data-db]
           (is (= (set (map #(take 2 %) gen-data))
                  #{[:user :u0]
                    [:todo-list :tl0]
                    [:todo :t0]
                    [:todo :t1]}))

           (let [ent-map (into {} (map #(vec (drop 1 %)) gen-data))]
             (is (submap? {:u0 {:username "Luigi"}
                           :t0 {:todo-title "write unit tests"}
                           :t1 {:todo-title "write unit tests"}}
                          ent-map))
             (is (ids-present? ent-map))
             (is (ids-match? ent-map
                             {:tl0 {:created-by-id [:u0 :id]
                                    :updated-by-id [:u0 :id]}
                              :t0  {:created-by-id [:u0 :id]
                                    :updated-by-id [:u0 :id]
                                    :todo-list-id  [:tl0 :id]}
                              :t1  {:created-by-id [:u0 :id]
                                    :updated-by-id [:u0 :id]
                                    :todo-list-id  [:tl0 :id]}})))))))

   "spec"
   {:schema   spec-schema
    :generate {:generator spec-generator}}

   "malli"
   {:schema   malli-schema
    :generate {:generator malli-generator}}
   ))

;;---
;; cycles
;;---

(defn insert-cycle
  [db {:keys [ent-name]}]
  (swap! gen-data-cycle-db conj ent-name)
  (dd/ent-attr db ent-name :generate))

(deftest test-handle-cycles-with-constraints-and-reordering
  (ct/do-template
   [generator-name ent-db]
   (testing generator-name
     (testing "todo-list is inserted before todo because todo requires todo-list"
       (reset! gen-data-cycle-db [])
       (-> (dd/generate
            ent-db
            {:todo [[1]]})
           (dd/visit-ents :insert-cycle insert-cycle))
       (is (= [:tl0 :t0]
              @gen-data-cycle-db))))

   "spec"
   {:schema   spec-cycle-schema
    :generate {:generator spec-generator}}

   "malli"
   {:schema   malli-cycle-schema
    :generate {:generator malli-generator}}
   ))

(deftest test-handles-cycle-ids
  (ct/do-template
   [generator-name ent-db]
   (testing generator-name
     (testing "generate correctly sets foreign keys for cycles"
       (let [gen (dd/generate-attr-map
                  ent-db
                  {:todo [[1]]})]
         (is (ids-present? gen))
         (is (ids-match? gen
                         {:t0  {:todo-list-id [:tl0 :id]}
                          :tl0 {:first-todo-id [:t0 :id]}})))))

   "spec"
   {:schema   spec-cycle-schema
    :generate {:generator spec-generator}}

   "malli"
   {:schema   malli-cycle-schema
    :generate {:generator malli-generator}}
   ))

(deftest test-throws-exception-on-2nd-map-ent-attr-try
  (testing "insert-cycle fails because the schema contains a :required cycle"
    (is (thrown-with-msg? #?(:clj clojure.lang.ExceptionInfo
                             :cljs js/Object)
                          #"Can't sort ents: check for cycles in ent type relations"
                          (-> (dd/add-ents {:schema {:todo      {:spec      ::todo
                                                                 :relations {:todo-list-id [:todo-list :id]}
                                                                 :prefix    :t}
                                                     :todo-list {:spec      ::todo-list
                                                                 :relations {:first-todo-id [:todo :id]}
                                                                 :prefix    :tl}}}
                                           {:todo [[1]]})
                              (dd/visit-ents :insert-cycle insert-cycle))))))
