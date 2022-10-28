(ns donut.datapotato-tutorial.05
  (:require [donut.datapotato.core :as dc]))

(def potato-schema
  {:user  {:prefix :u}
   :topic {:prefix    :t
           :relations {:owner-id [:user :id]}}})

(def potato-db
  {:schema potato-schema})

(defn ex-01
  []
  (let [potato-db-1 (dc/add-ents {:schema potato-schema} {:topic [{:count 1}]})
        potato-db-2 (dc/add-ents potato-db-1 {:topic [{:count 1}
                                                      {:refs {:owner-id :hamburglar}}]})]
    (dc/view potato-db-1)
    (dc/view potato-db-2)))

(ex-01)
