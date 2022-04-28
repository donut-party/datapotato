(ns donut.datapotato.generate.spec-test
  (:require
   [clojure.spec.alpha :as s]
   [clojure.spec.gen.alpha :as sg]
   #?(:clj [clojure.test :refer [deftest]]
      :cljs [cljs.test :include-macros true :refer [deftest]])
   [donut.datapotato.test-schemas :as ts]
   [donut.datapotato.generate.test-helpers :as dgth]))

;;---
;; specs
;;---

(s/def ::id (s/with-gen pos-int?
              (constantly ts/monotonic-id-gen)))

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

(def schema
  (-> ts/schema
      (assoc-in [:user :generate :schema] ::user)
      (assoc-in [:todo :generate :schema] ::todo)
      (assoc-in [:todo-list :generate :schema] ::todo-list)
      (assoc-in [:todo-list-watch :generate :schema] ::todo-list-watch)
      (assoc-in [:project :generate :schema] ::project)))


(def cycle-schema
  (-> ts/cycle-schema
      (assoc-in [:user :generate :schema] ::user)
      (assoc-in [:todo :generate :schema] ::user)
      (assoc-in [:todo-list :generate :schema] ::todo-list)))

(s/def ::topic-category (s/keys :req-un [::id]))

(s/def ::topic-category-id ::id)
(s/def ::topic (s/keys :req-un [::id ::topic-category-id]))

(s/def ::watched-id ::id)
(s/def ::watch (s/keys :req-un [::id ::watched-id]))

(def polymorphic-schema
  (-> ts/polymorphic-schema
      (assoc-in [:topic-category :generate :schema] ::topic-category)
      (assoc-in [:topic :generate :schema] ::todo)
      (assoc-in [:watch :generate :schema] ::watch)))


(deftest test-generate-suite
  (dgth/run-generate-test-suite {:schema       schema
                                 :cycle-schema cycle-schema
                                 :generator    (comp sg/generate s/gen)}))
