(ns donut.datapotato-tutorial.03
  "queries"
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

(comment
  (dc/view (ex-01) :fmt :svg)
  (dc/view (ex-02) :fmt :svg)
  (dc/view (ex-03) :fmt :svg))
