(ns donut.datapotato-tutorial.08
  (:require [donut.datapotato.core :as dc]
            [malli.generator :as mg]))

(def User
  [:map
   [:id pos-int?]
   [:favorite-ids [:vector pos-int?]]])

(def Topic
  [:map
   [:id pos-int?]])

(def potato-schema
  {:user  {:prefix      :u
           :generate    {:schema User}
           :relations   {:favorite-ids [:topic :id]}
           :constraints {:favorite-ids #{:coll}}}
   :topic {:prefix   :t
           :generate {:schema Topic}}})

(def potato-db
  {:schema   potato-schema
   :generate {:generator mg/generate}})

(defn ex-01
  []
  (dc/generate potato-db {:user [{:count 1}]}))

(defn ex-02
  []
  (dc/generate potato-db {:user [{:refs {:favorite-ids 3}}]}))

(defn ex-03
  []
  (dc/view (dc/add-ents potato-db
                        {:user [{:refs {:count 2
                                        :favorite-ids 3}}]})))

(defn ex-04
  []
  (dc/view (dc/add-ents potato-db
                        {:user [[1 {:refs {:favorite-ids [:my-p0 :my-p1]}}]
                                [1 {:refs {:favorite-ids [:my-p2 :my-p3]}}]]})))
