(ns donut.datapotato.test-schemas
  (:require
   [clojure.data :as data]
   [clojure.test.check.generators :as gen :include-macros true]))

(def id-seq (atom 0))
(def monotonic-id-gen
  (gen/fmap (fn [_] (swap! id-seq inc)) (gen/return nil)))


;; Test helper functions
(defn submap?
  "All vals in m1 are present in m2"
  [m1 m2]
  (nil? (first (data/diff m1 m2))))

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
