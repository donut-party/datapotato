(ns donut.datapotato.test-data
  (:require
   [clojure.data :as data]))

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
  {:user            {:generate {:schema User}
                     :prefix   :u}
   :todo            {:generate  {:schema     Todo
                                 :overwrites {:todo-title "write unit tests"}}
                     :relations {:created-by-id [:user :id]
                                 :updated-by-id [:user :id]
                                 :todo-list-id  [:todo-list :id]}
                     :prefix    :t}
   :todo-list       {:generate  {:schema TodoList}
                     :relations {:created-by-id [:user :id]
                                 :updated-by-id [:user :id]}
                     :prefix    :tl}
   :todo-list-watch {:generate    {:schema TodoListWatch}
                     :relations   {:todo-list-id [:todo-list :id]
                                   :watcher-id   [:user :id]}
                     :constraints {:todo-list-id #{:uniq}}
                     :prefix      :tlw}
   :project         {:generate    {:schema Project}
                     :relations   {:created-by-id [:user :id]
                                   :updated-by-id [:user :id]
                                   :todo-list-ids [:todo-list :id]}
                     :constraints {:todo-list-ids #{:coll}}
                     :prefix      :p}})

(def cycle-schema
  {:user      {:generate  {:schema User}
               :prefix    :u
               :relations {:updated-by-id [:user :id]}}
   :todo      {:generate         {:schema Todo}
               :relations        {:todo-list-id [:todo-list :id]}
               :constraints      {:todo-list-id #{:required}}
               :malli-schema-gen {:todo-title "write unit tests"}
               :prefix           :t}
   :todo-list {:generate  {:schema TodoList}
               :relations {:first-todo-id [:todo :id]}
               :prefix    :tl}})

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
  {:topic-category {:malli-schema TopicCategory
                    :prefix       :tc}
   :topic          {:malli-schema Topic
                    :relations    {:topic-category-id [:topic-category :id]}
                    :prefix       :t}
   :watch          {:malli-schema Watch
                    :relations    {:watched-id #{[:topic-category :id]
                                                 [:topic :id]}}
                    :prefix       :w}})
