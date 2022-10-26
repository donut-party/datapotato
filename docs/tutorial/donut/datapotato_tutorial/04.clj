(ns donut.datapotato-tutorial.04
  (:require [donut.datapotato.core :as dc]))

(def potato-schema
  {:user  {:prefix :u}
   :topic {:prefix    :t
           :relations {:owner-id [:user :id]}}
   :post  {:prefix    :p
           :relations {:topic-id [:topic :id]
                       :owner-id [:user :id]}}})

(def potato-db
  {:schema potato-schema})

(defn ex-01
  []
  (dc/add-ents potato-db {:topic [{:count 2
                                   :refs  {:owner-id :my-own-sweet-user}}
                                  {:count 1}]}))

(defn ex-02
  []
  (dc/add-ents potato-db {:topic [{:count 1}
                                  {:refs  {:owner-id :hamburglar}}]
                          :post  [{:count 1}
                                  {:refs  {:topic-id :t1}}]}))

(defn ex-03
  []
  (dc/add-ents potato-db {:topic [{:ent-name :t0}
                                  {:ent-name :t1
                                   :refs     {:owner-id :hamburglar}}]
                          :post  [{:refs {:topic :tl0}}
                                  {:refs {:topic :tl1}}]}))
