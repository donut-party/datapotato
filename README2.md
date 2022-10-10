# Datapotato: better database fixtures for tests!

* [Purpose](#purpose)
* [Getting started](#getting-started)
* [Docs](#docs)

## Purpose

Datapotato lets you manage test fixtures in a way that's clear, concise, and
easy to maintain. It's great for dramatically reducing test boilerplate.

Say you want to test a scenario where a forum post has gotten three likes by
three different users. You'd first have to create a hierarchy of records for the
post, topic, topic category, and users. You have to make sure that all the
foreign keys are correct (e.g. the post's `:topic-id` is set to the topic's
`:id`) and that everything is inserted in the right order.

Without Datapotato, you'd have to write code like this 😭

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

With Datapotato, all you have to do is **write code like this**:

```clojure
(dc/with-fixtures
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

When you're dealing with fixture data by hand, you end up obscuring the your
code's intent because you have to create so many let bindings that aren't
directly related to what you're trying to test; it's hard to see what's relevant
and what's not. What's more, you have to slog through the tedium of making sure
that foreign keys are set correctly. You weren't meant to spend your one wild
and precious life making sure you lined up your test ids right.

Datapotato handles all that for you, and the result is something that's easier
to write and easier to understand.

## Example

This example shows the pieces you need to set up so that you can start using
Datapotato to generate and insert fixtures.

## Docs

The docs answer questions like:

* How do I customize the records that get generated?
* How do I refer to generated records?
