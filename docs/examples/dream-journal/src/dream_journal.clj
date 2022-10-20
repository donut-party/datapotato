(ns dream-journal
  (:require
   [donut.datapotato.atom :as da]
   [donut.datapotato.core :as dc]
   [malli.generator :as mg]))

(def User
  [:map
   [:id pos-int?]
   [:username string?]])

(def DreamJournal
  [:map
   [:id pos-int?]
   [:owner-id pos-int?]
   [:dream-journal-name string?]])

(def Entry
  [:map
   [:id pos-int?]
   [:dream-journal-id pos-int?]
   [:content string?]])

;; use mg/generate to generate examples
(mg/generate User)

;; =>
{:id 37550, :username "EiaB5V3xqYDa11x7rZ"}

;;---
;; now we set up datapotato
;;---

(def fixture-atom (atom []))

(def potato-schema
  {:user          {:prefix   :u
                   :generate {:schema User}}
   :dream-journal {:prefix    :dj
                   :generate  {:schema DreamJournal}
                   :relations {:owner-id [:user :id]}}
   :entry         {:prefix    :e
                   :generate  {:schema Entry}
                   :relations {:dream-journal-id [:dream-journal :id]}}})

(def potato-db
  {:schema   potato-schema
   :generate {:generator mg/generate}
   :fixtures {:insert da/insert
              :setup  (fn [_] (reset! fixture-atom []))
              :atom   fixture-atom}})

;;---
;; populate the atom
;;---

(dc/with-fixtures potato-db
  (dc/insert-fixtures {:user [{:count 3}]}))

(dc/with-fixtures potato-db
  (dc/insert-fixtures {:entry [{:count 2}]}))


(dc/with-fixtures potato-db
  (dc/insert-fixtures {:entry [{:generate {:set {:content "hotdogs again."}}}]}))
