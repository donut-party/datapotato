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
;; stored in `id-atom`. This ensures unique ids and produces predictable values
(def id-atom (atom 0))
(def monotonic-id-gen
  (gen/fmap (fn [_] (swap! id-atom inc)) (gen/return nil)))

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

(def potato-schema
  {:user {:prefix   :u
          :generate {:schema User}
          :fixtures {:table-name "users"}}
   :post {:prefix    :p
          :generate  {:schema Post}
          :fixtures  {:table-name "posts"}
          :relations {:post/created-by-id [:user :user/id]}}
   :like {:prefix      :l
          :generate    {:schema Like}
          :fixtures    {:table-name "likes"}
          :relations   {:like/post-id       [:post :post/id]
                        :like/created-by-id [:user :user/id]}
          :constraints {:like/created-by-id #{:uniq}}}})

;; The potato-db contains configuration for generating records and ensuring their
;; foreign keys are correct, and for managing test lifecycle
(def potato-db
  {:schema   potato-schema
   :generate {:generator mg/generate}
   :fixtures {:insert da/insert
              :setup  (fn [_]
                        (reset! mock-db [])
                        (reset! id-atom 0))
              :atom   mock-db}})



;;-------*****--------
;; Begin snippets to try in REPL
;;-------*****--------

;; The next two examples show that records are inserted into the simulated
;; "database" (`mock-db`) in correct dependency order:

;; example 1
(dc/with-fixtures potato-db
  (dc/insert-fixtures {:like [{:count 1}]}))
@mock-db
;; =>
[[:user #:user{:id 1, :username "l6DvOOzTz4BnuUV8E6DMOqx5"}]
 [:post #:post{:id 2, :created-by-id 1, :content "2axPd3IG6"}]
 [:like #:like{:id 3, :post-id 2, :created-by-id 1}]]

;; example 2
(dc/with-fixtures potato-db
  (dc/insert-fixtures {:post [{:count 2}]
                       :like [{:count 3}]}))
@mock-db
;; =>
[[:user #:user{:id 1, :username "OW84O7k2Jor1bW6aK3"}]
 [:user #:user{:id 2, :username "7MkRWt27W2q29Ehev"}]
 [:post #:post{:id 3, :created-by-id 2, :content "i1xCUw8"}]
 [:like #:like{:id 4, :post-id 3, :created-by-id 1}]
 [:like #:like{:id 5, :post-id 3, :created-by-id 2}]
 [:post #:post{:id 6, :created-by-id 2, :content "0JBdK4y7J7s0"}]
 [:user #:user{:id 7, :username "42QN79LEmJ6NCw85oaSqmq1"}]
 [:like #:like{:id 8, :post-id 3, :created-by-id 7}]]


;; The examples below show how you can experiment with generating data without
;; inserting it.

;; Return a map of user entities and their spec-generated data
(dc/generate potato-db {:user [{:count 3}]})

;; You can specify a username and id
(dc/generate potato-db {:user [{:count    1
                                :generate {:username "Meeghan"
                                           :id       100}}]})

;; Generating a post generates the user the post belongs to, with
;; foreign keys correct
(dc/generate potato-db {:post [{:count 1}]})

;; Generating a like also generates a post and user
(dc/generate potato-db {:like [{:count 1}]})
