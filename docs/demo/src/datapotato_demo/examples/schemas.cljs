(ns datapotato-demo.examples.schemas
  (:require [clojure.spec.alpha :as s]
            [clojure.test.check.generators :as gen :include-macros true]
            [shadow.resource :as rc]))

(def id-atom (atom 0))

(s/def ::id
  (s/with-gen
    pos-int?
    #(gen/fmap (fn [_] (swap! id-atom inc)) (gen/return nil))))


(s/def ::user-name #{"Maggie" "Linh" "Bubba" "Tomm" "Rory" "Link" "Janie"})
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

(def todo-schema
  {:user            {:spec   ::user
                     :prefix :u}
   :attachment      {:spec      ::attachment
                     :relations {:created-by-id [:user :id]
                                 :updated-by-id [:user :id]
                                 :todo-id       [:todo :id]}
                     :prefix    :a}
   :todo            {:spec      ::todo
                     :relations {:created-by-id [:user :id]
                                 :updated-by-id [:user :id]
                                 :todo-list-id  [:todo-list :id]}
                     :spec-gen  {:todo-title "write unit tests"}
                     :prefix    :t}
   :todo-list       {:spec      ::todo-list
                     :relations {:created-by-id [:user :id]
                                 :updated-by-id [:user :id]}
                     :prefix    :tl}
   :todo-list-watch {:spec        ::todo-list-watch
                     :relations   {:todo-list-id [:todo-list :id]
                                   :watcher-id   [:user :id]}
                     :constraints {:todo-list-id #{:uniq}}
                     :prefix      :tlw}
   :project         {:spec        ::project
                     :relations   {:created-by-id [:user :id]
                                   :updated-by-id [:user :id]
                                   :todo-list-ids [:todo-list :id]}
                     :constraints {:todo-list-ids #{:coll}}
                     :prefix      :p}})


(def todo-schema-txt
  (rc/inline "./todo-schema.edn"))
