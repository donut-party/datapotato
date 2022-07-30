(ns donut.datapotato.atom
  "Generates fixtures for atom 'storage'"
  (:require
   [donut.datapotato.core :as dc]))

(defn perform-insert
  [{{:keys [atom]} dc/fixtures-visit-key}
   {:keys [ent-type visit-val]}]
  (swap! atom conj [ent-type visit-val])
  visit-val)
