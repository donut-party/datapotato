(ns donut.datapotato-tutorial.01
  (:require [donut.datapotato.core :as dc]))

(def potato-schema
  {:user {:prefix :u}})

(def potato-db
  {:schema potato-schema})

(defn ex-01
  []
  (dc/add-ents potato-db {:user [{:count 3}]}))

(dc/view (ex-01) :fmt :svg)

(-> (ex-01) (dc/ents-by-type))

(-> (ex-01) (dc/ent-relations :u0))

(-> (ex-01) (dc/all-ent-relations))

(comment
  ;; evaluating this:
  (ex-01)

  ;; produces this:
  {:schema         {:user {:prefix :u}}
   :data           {:nodeset #{:u1 :u0 :u2 :user}
                    :adj     {:user #{:u1 :u0 :u2}}
                    :in      {:u0 #{:user} :u1 #{:user} :u2 #{:user}}
                    :attrs   {:user {:type :ent-type}
                              :u0   {:type :ent :index 0 :ent-type :user :query-term [3]}
                              :u1   {:type :ent :index 1 :ent-type :user :query-term [3]}
                              :u2   {:type :ent :index 2 :ent-type :user :query-term [3]}}}
   :queries        [{:user [[3]]}]
   :relation-graph {:nodeset #{:user} :adj {} :in {}}
   :types          #{:user}
   :ref-ents       []})
