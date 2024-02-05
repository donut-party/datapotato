(ns donut.datapotato-tutorial.02
  (:require
   [donut.datapotato.core :as dc]))

(def potato-schema
  {:user {:prefix :u}
   :post {:prefix    :p
          :relations {:owner-id [:user :id]}}})

(def potato-db
  {:schema potato-schema})

(defn ex-01
  []
  (dc/add-ents potato-db {:post [{:count 2}]}))

(dc/view (ex-01) :fmt :svg)

(-> (ex-01) (dc/ents-by-type))

(-> (ex-01) (dc/ent-relations :u0))

(-> (ex-01) (dc/all-ent-relations))
