(ns short-sweet
  (:require
   [clojure.test.check.generators :as gen :include-macros true]
   [donut.datapotato.atom :as da]
   [donut.datapotato.core :as dc]
   [malli.generator :as mg]))

;;-------*****--------
;; Begin example setup
;;-------*****--------

;; ---
;; Define specs for our domain entities

;; IDs should be a positive int, and to generate it we increment the number
;; stored in `id-seq`. This ensures unique ids and produces predictable values
(def id-seq (atom 0))
(def monotonic-id-gen
  (gen/fmap (fn [_] (swap! id-seq inc)) (gen/return nil)))

(def ID
  [:and {:gen/gen monotonic-id-gen} pos-int?])

(def User
  [:map
   [:user/id ID]
   [:user/username string?]])

(def Post
  [:map
   [:post/id ID]
   [:post/created-by-id pos-int?]
   [:post/content string?]])


(def Like
  [:map
   [:like/id ID]
   [:like/post-id pos-int?]
   [:like/created-by-id pos-int?]])

;; ---
;; Our "db" is an atom holding a vector of inserted records we can use to show
;; that entities are inserted in the correct order
(def mock-db (atom []))

;; The datapotato schema defines `ent-types`, which roughly correspond to db
;; tables. Below, the ent-types are `:user`, `:post`, and `:like.` The ent-type
;; schemas include a `:generate` key, which includes the `:schema` used to
;; generate records fo that type. The `:relations` key specifies how ents
;; reference each other. Relations correspond to foreign keys.

(def datapotato-schema
  {:user {:prefix   :u
          :generate {:schema User}
          :fixtures {:table-name "users"}}
   :post {:prefix    :p
          :generate  {:schema Post}
          :fixtures  {:table-name "posts"}
          :relations {:created-by-id [:user :id]}}
   :like {:prefix      :l
          :generate    {:schema Like}
          :fixtures    {:table-name "likes"}
          :relations   {:post-id       [:post :id]
                        :created-by-id [:user :id]}
          :constraints {:created-by-id #{:uniq}}}})

;; The potatodb contains configuration for generating records and ensuring their
;; foreign keys are correct, and for managing test lifecycle
(def potatodb
  {:schema   datapotato-schema
   :generate {:generator mg/generate}
   :fixtures {:insert da/insert
              :setup  (fn [_]
                        (reset! mock-db [])
                        (reset! id-seq 0))
              :atom   mock-db}})



;;-------*****--------
;; Begin snippets to try in REPL
;;-------*****--------

;; The next two examples show that records are inserted into the simulated
;; "database" (`mock-db`) in correct dependency order:
(dc/with-fixtures potatodb
  (dc/insert-fixtures {:like [{:count 1}]}))
@mock-db

(dc/with-fixtures potatodb
  (dc/insert-fixtures {:post [{:count 2}]
                       :like [{:count 3}]}))
@mock-db

;; The examples below show how you can experiment with generating data without
;; inserting it.

;; Return a map of user entities and their spec-generated data
(dc/generate-attr-map potatodb {:user [{:count 3}]})

;; You can specify a username and id
(dc/generate-attr-map potatodb {:user [{:count      1
                                        :generate {:username "Meeghan"
                                                   :id       100}}]})

;; Generating a post generates the user the post belongs to, with
;; foreign keys correct
(dc/generate-attr-map potatodb {:post [{:count 1}]})

;; Generating a like also generates a post and user
(dc/generate-attr-map potatodb {:like [{:count 1}]})
