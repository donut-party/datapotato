(ns donut.datapotato-tutorial.02
  (:require [donut.datapotato.core :as sm]
            [loom.io :as lio]))

(def potato-schema
  {:user {:prefix :u}
   :post {:prefix    :p
          :relations {:owner-id [:user :id]}}})

(def potato-db
  {:schema potato-schema})

(defn ex-01
  []
  (sm/add-ents potato-db {:post [{:count 2}]}))

(-> (ex-01) (sm/ents-by-type))

(-> (ex-01) (sm/ent-relations :u0))

(-> (ex-01) (sm/all-ent-relations))
