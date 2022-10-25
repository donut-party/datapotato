(ns donut.datapotato-tutorial.03
  (:require [donut.datapotato.core :as dc]))

(def potato-schema
  {:user {:prefix :u}
   :post {:prefix    :p
          :relations {:owner-id [:user :id]}}})

(def potato-db
  {:schema potato-schema})

(defn ex-01
  []
  (dc/add-ents potato-db {:post [{:count 2}]}))

(defn ex-02
  []
  (dc/add-ents potato-db {:user [{:ent-name :admin}]}))

(defn ex-03
  []
  (dc/add-ents potato-db {:post [{:count 3}
                                 {:ent-name :most-favorited-post}]}))
