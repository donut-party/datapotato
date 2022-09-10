(ns donut.datapotato.datomic
  (:require
   [donut.datapotato.core :as dc]
   [datomic.api :as d]))

(defn insert
  [{{:keys [connection]} dc/fixtures-visit-key}
   {:keys [visit-val]}]
  (let [result @(d/transact connection [visit-val])
        dbid (first (vals (:tempids result)))]
    (assoc (d/pull (d/db connection) '[*] dbid)
           :db/id dbid)))
