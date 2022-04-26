(ns donut.datapotato.spec-test-data
  (:require
   [clojure.spec.alpha :as s]
   [clojure.test.check.generators :as gen :include-macros true]
   [clojure.data :as data]
   [donut.datapotato.test-data :as td]))

;; Test helper functions
(defn submap?
  "All vals in m1 are present in m2"
  [m1 m2]
  (nil? (first (data/diff m1 m2))))

(def id-seq (atom 0))

(defn test-fixture [f]
  (reset! id-seq 0)
  (f))

(s/def ::id
  (s/with-gen
    pos-int?
    #(gen/fmap (fn [_] (swap! id-seq inc)) (gen/return nil))))


(s/def ::user-name #{"Luigi"})
(s/def ::user (s/keys :req-un [::id ::user-name]))

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

(def schema
  (-> td/schema
      (assoc-in [:user :generate :spec] ::user)
      (assoc-in [:todo :generate :spec] ::todo)
      (assoc-in [:todo-list :generate :spec] ::todo-list)
      (assoc-in [:todo-list-watch :generate :spec] ::todo-list-watch)
      (assoc-in [:project :generate :spec] ::project)))


(def cycle-schema
  (-> td/cycle-schema
      (assoc-in [:user :generate :spec] ::user)
      (assoc-in [:todo :generate :spec] ::user)
      (assoc-in [:todo-list :generate :spec] ::todo-list)))

(s/def ::topic-category (s/keys :req-un [::id]))

(s/def ::topic-category-id ::id)
(s/def ::topic (s/keys :req-un [::id ::topic-category-id]))

(s/def ::watched-id ::id)
(s/def ::watch (s/keys :req-un [::id ::watched-id]))

(def polymorphic-schema
  (-> td/polymorphic-schema
      (assoc-in [:topic-category :generate :spec] ::topic-category)
      (assoc-in [:topic :generate :spec] ::todo)
      (assoc-in [:watch :generate :spec] ::watch)))
