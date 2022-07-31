(ns donut.datapotato.xtdb
  (:require
   [donut.datapotato.core :as dc]
   [xtdb.api :as xt]))

(defn perform-insert
  [{{:keys [connection]} dc/fixtures-visit-key}
   {:keys [ent-name visit-val]}]
  (xt/submit-tx connection [[::xt/put (merge {:xt/id ent-name} visit-val)]])
  (xt/sync connection)
  visit-val)
