(ns donut.datapotato-tutorial.06
  (:require [donut.datapotato.core :as dc]
            [malli.generator :as mg]))

(def User
  [:map
   [:id pos-int?]
   [:username string?]])

(def Topic
  [:map
   [:id pos-int?]
   [:owner-id pos-int?]
   [:name string?]])

(def potato-schema
  {:user  {:prefix   :u
           :generate {:schema User}}
   :topic {:prefix    :t
           :generate  {:schema Topic}
           :relations {:owner-id [:user :id]}}})

(def potato-db
  {:schema   potato-schema
   :generate {:generator mg/generate}})

(defn ex-01
  []
  (dc/generate potato-db {:user [{:count 1}]}))

(ex-01)


(defn ex-02
  []
  (dc/generate potato-db {:user [{:count 1
                                  :set   {:username "nohohank"}}]}))
(ex-02)

(defn ex-03
  []
  (let [result (dc/generate potato-db {:user [{:count 1
                                               :set   {:username "nohohank"}}]})]
    (dc/generate result {:user [{:count 1
                                 :set   {:username "bartleby"}}]})))
(ex-03)


(defn ex-04
  []
  (dc/generate potato-db {:topic [{:count 1}]}))


(defn ex-05
  []
  (dc/generate potato-db {:topic [{:count 1
                                   :refs  {:owner-id ::dc/omit}}]}))
