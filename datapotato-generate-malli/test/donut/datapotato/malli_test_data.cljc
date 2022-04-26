(ns donut.datapotato.malli-test-data
  (:require
   [clojure.data :as data]
   [donut.datapotato.test-data :as td]))

;; Test helper functions
(defn submap?
  "All vals in m1 are present in m2"
  [m1 m2]
  (nil? (first (data/diff m1 m2))))

(def ID pos-int?)

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

(def schema
  (-> td/schema
      (assoc-in [:user :generate :schema] User)
      (assoc-in [:todo :generate :schema] Todo)
      (assoc-in [:todo-list :generate :schema] TodoList)
      (assoc-in [:todo-list-watch :generate :schema] TodoListWatch)
      (assoc-in [:project :generate :schema] Project)))

(def cycle-schema
  (-> td/cycle-schema
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

(def polymorphic-schema
  (-> td/polymorphic-schema
      (assoc-in [:topic-category-id :generate :schema] TopicCategory)
      (assoc-in [:topic :generate :schema] Todo)
      (assoc-in [:watch :generate :schema] Watch)))
