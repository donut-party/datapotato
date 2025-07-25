# datapotato: better database fixtures for tests! 🥔

[![Clojars Project](https://img.shields.io/clojars/v/party.donut/datapotato.svg)](https://clojars.org/party.donut/datapotato)

* [Purpose](#purpose)
* [Example](#example)
* [Spec Example](#spec-example)
* [Docs](#docs)

## Purpose

datapotato lets you manage test fixtures in a way that's clear, concise, and
easy to maintain. It's great for dramatically reducing test boilerplate. It has
integrations for
[next-jdbc](https://github.com/donut-party/datapotato/wiki/database-integration#next-jdbc),
[XTDB](https://github.com/donut-party/datapotato/wiki/database-integration#xtdb),
and
[datomic](https://github.com/donut-party/datapotato/wiki/database-integration#datomic).
[Fluree](https://github.com/donut-party/datapotato/blob/main/datapotato-fluree/src/donut/datapotato/fluree.clj)
integration is in progress.

Say you want to test a scenario where a forum post has gotten three likes by
three different users. You'd first have to create a hierarchy of records for the
post, topic, topic category, and users. You have to make sure that all the
foreign keys are correct (e.g. the post's `:topic-id` is set to the topic's
`:id`) and that everything is inserted in the right order.

Normally, you'd have to write code like this 😭

```clojure
(let [user-1         (insert :users (generate-user))
      user-2         (insert :users (generate-user))
      user-3         (insert :users (generate-user))
      topic-category (insert :topic-categories (generate-topic-category {:created-by-id (:id user-1)
                                                                         :updated-by-id (:id user-1)}))
      topic          (insert :topics (generate-topic {:topic-category-id (:id topic-category)
                                                      :created-by-id     (:id user-1)
                                                      :updated-by-id     (:id user-1)}))
      post           (insert :posts (generate-post {:topic-id      (:id topic)
                                                    :created-by-id (:id user-1)
                                                    :updated-by-id (:id user-1)}))
      like-1         (insert :likes (generate-like {:post-id       (:id post)
                                                    :created-by-id (:id user-1)}))
      like-2         (insert :likes (generate-like {:post-id       (:id post)
                                                    :created-by-id (:id user-2)}))
      like-3         (insert :likes (generate-like {:post-id       (:id post)
                                                    :created-by-id (:id user-3)}))])
```

With datapotato, all you have to do is **write code like this**:

```clojure
(dc/with-fixtures potato-db
  (dc/insert-fixtures {:like [{:count 3}]}))
```

and **these records get inserted** in a database (in the order displayed):

```clojure
[[:user {:id 1 :username "T2TD3pAB79X5"}]
 [:user {:id 2 :username "ziJ9GnvNMOHcaUz"}]
 [:topic-category {:id 3 :created-by-id 2 :updated-by-id 2}]
 [:topic {:id 6
          :topic-category-id 3
          :title "4juV71q9Ih9eE1"
          :created-by-id 2
          :updated-by-id 2}]
 [:post {:id 10 :topic-id 6 :created-by-id 2 :updated-by-id 2}]
 [:like {:id 14 :post-id 10 :created-by-id 1}]
 [:like {:id 17 :post-id 10 :created-by-id 2}]
 [:user {:id 20 :username "b73Ts5BoO"}]
 [:like {:id 21 :post-id 10 :created-by-id 20}]]
```

When you're dealing with fixture data by hand, you end up obscuring your code's
intent because you have to create so many let bindings that aren't directly
related to what you're trying to test; it's hard to see what's relevant and
what's not. What's more, you have to slog through the tedium of making sure that
foreign keys are set correctly. You weren't meant to spend your one wild and
precious life making sure you lined up your test ids right.

datapotato handles all that for you, and the result is something that's easier
to write and easier to understand.

## Example

This example shows the pieces you need to start using datapotato to generate and
insert fixtures:

* Specs to generate data (malli is used here, but you can also use clojure.spec
  or plumatic schema or even your own bespoke data generators)
* A `potato-db` configuration, which includes:
  * A schema that tells datapotato what types of entities there are, how they're
    related, and how to generate them
  * Configuration for data generation
  * Configuration for insertion

It doesn't interact with a real database. Rather, it adds generated data to an
atom containing a vector. (See [database
integration](../../wiki/database-integration) for instructions on working
with a real database.)

``` clojure
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
;; generate records for that type. The `:relations` key specifies how ents
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
```

## Spec Example

To use clojure spec, you'd write code that looks like this:


``` clojure
(s/def ::user ...)
(def potato-schema
  {:user {:prefix   :u
          :generate {:schema ::user}
          :fixtures {:table-name "users"}}})

(def potato-db
  {:schema   potato-schema
   :generate {:generator (comp clojure.spec.gen.alph/generate clojure.spec.alpha/gen)}
   :fixtures {:insert da/insert
              :setup  (fn [_]
                        (reset! mock-db [])
                        (reset! id-atom 0))
              :atom   mock-db}})
```

Note the use of `::user` and `(comp clojure.spec.gen.alph/generate
clojure.spec.alpha/gen)`.

## Docs

Extended docs are in the [wiki](../../wiki). Docs include:

* [Getting Started](../../wiki/getting-started), begin using datapotato for your
  project
* [Database integration](../../wiki/database-integration) has instructions on
  working with datomic, next-jdbc, xtdb, or the database of your choice
* [Tutorial](../../wiki/01-potato-db), a walkthrough that covers some of
  datapotato's underlying mechanisms and explains how to handle more complicated
  scenarios, including:
  * Uniqueness constraints (when two records shouldn't have the same pair of
    foreign keys, e.g. when your app allows users to like posts, but a post
    shouldn't be liked twice by the same user)
  * Collection constraints (when a field refers to a sequence of foreign keys)
  * Polymorphic references (when a field can refer to entities of different
    types)
* [Visiting Functions](../../wiki/visiting-functions), a look at one of
  datapotato's underlying interfaces that is unlikely to be relevant for
  day-to-day work but may be useful if you're trying to extend the library

## Forking Specmonstah

This is a fork of [Specmonstah](https://github.com/reifyhealth/specmonstah). It
should be 100% backwards-compatible; if you're using Specmonstah, the only thing
you should have to change when switching to datapotato is namespace requires.

Changes include:

* **A flexible model for handling data generation.** The old model
  provided out-of-the-box support for clojure.spec, but it was difficult to
  understand how to support another tool like malli or plumatic schema. The new
  model makes data generation pluggable.
* **First-class support for inserting fixtures into databases.** With
  Specmonstah, you have to roll your own tools for inserting the generated data
  into a database, and learning how to do that is a bit of a time commitment.
  datapotato provides helper libraries for working with xtdb, next-jdbc, and
  datomic.
  
  Its improved design makes it easier for you to roll your own database
  integration, if you need to. You only have to learn the insert API, instead of
  having to learn the entire underlying model.
* **Fixture lifecycle management.** The `with-fixtures` macro makes it easier to
  manage fixture setup and teardown.
* **User friendlier.** The main idea behind the fork is to provide interfaces
  that are optimized for the primary intended use case, working with test
  fixtures, rather than giving you the building blocks and asking you to
  assemble them yourself. The building blocks are still there, but these changes
  should make it easier for the majority of devs to do something useful with
  this library.
