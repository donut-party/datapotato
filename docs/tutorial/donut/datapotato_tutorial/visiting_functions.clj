(ns donut.datapotato-tutorial.visiting-functions
  (:require [donut.datapotato.core :as dc]))

(def schema
  {:user  {:prefix :u}
   :topic {:prefix    :t
           :relations {:owner-id [:user :id]}}
   :post  {:prefix    :p
           :relations {:topic-id [:topic :id]}}})

(defn announce
  [_potato-db {:keys [ent-name]}]
  (str "announcing... " ent-name "!"))

(defn ex-01
  []
  (-> (dc/add-ents {:schema schema} {:post [[1]]})
      (dc/visit-ents :announce announce)
      (get-in [:data :attrs])))

(ex-01)
;; =>
{:post  {:type :ent-type},
 :p0    {:type                 :ent,
         :index                0,
         :ent-type             :post,
         :query-term           [1],
         :loom.attr/edge-attrs {:t0 {:relation-attrs #{:topic-id}}},
         :announce             "announcing... :p0!"},
 :topic {:type :ent-type},
 :t0    {:type                 :ent,
         :index                0,
         :ent-type             :topic,
         :query-term           [:_],
         :loom.attr/edge-attrs {:u0 {:relation-attrs #{:owner-id}}},
         :announce             "announcing... :t0!"},
 :user  {:type :ent-type},
 :u0    {:type       :ent,
         :index      0,
         :ent-type   :user,
         :query-term [:_],
         :announce   "announcing... :u0!"}}
