(ns donut.datapotato-tutorial.07
  "uniqueness constraint"
  (:require [donut.datapotato.core :as dc]))

(def bad-schema
  {:user {:prefix :u}
   :post {:prefix :p}
   :like {:prefix      :l
          :spec        ::like
          :relations   {:post-id       [:post :id]
                        :created-by-id [:user :id]}}})

(defn ex-01
  []
  (dc/add-ents {:schema bad-schema} {:like [[3]]}))

(def good-schema
  {:user {:prefix :u}
   :post {:prefix :p}
   :like {:prefix      :l
          :spec        ::like
          :relations   {:post-id       [:post :id]
                        :created-by-id [:user :id]}
          :constraints {:created-by-id #{:uniq}}}})

(defn ex-02
  []
  (dc/add-ents {:schema good-schema} {:like [[3]]}))

(comment
  (dc/view (ex-01) :fmt :svg)
  (dc/view (ex-02) :fmt :svg))
