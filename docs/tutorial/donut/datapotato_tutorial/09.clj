(ns donut.datapotato-tutorial.09
  (:require [donut.datapotato.core :as dc]
            [malli.generator :as mg]))

(def User
  [:map
   [:id pos-int?]])

(def Topic
  [:map
   [:id pos-int?]])

(def Post
  [:map
   [:id pos-int?]])

(def Watch
  [:map
   [:watcher-id pos-int?]
   [:watched-id pos-int?]])

(def potato-schema
  {:user  {:prefix   :u
           :generate {:schema User}}
   :topic {:prefix    :t
           :generate  {:schema Topic}
           :relations {:owner-id [:user :id]}}
   :post  {:prefix    :p
           :generate  {:schema Post}
           :relations {:owner-id [:user :id]
                       :topic-id [:topic :id]}}
   :watch {:prefix    :w
           :generate  {:schema Watch}
           :relations {:watcher-id [:user :id]
                       :watched-id #{[:topic :id]
                                     [:post :id]}}}})

(def potato-db
  {:schema   potato-schema
   :generate {:generator mg/generate}})

(defn ex-01
  []
  ;; generate a watch for a post
  (dc/generate potato-db {:watch [{:ref-types {:watched-id :post}}]}))

(defn ex-02
  []
  ;; generate a watch for a topic
  (dc/generate potato-db {:watch [{:ref-types {:watched-id :topic}}]}))
