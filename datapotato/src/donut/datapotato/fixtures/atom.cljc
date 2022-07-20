(ns donut.datapotato.fixtures.atom
  "Generates fixtures for atom 'storage'"
  (:require
   [donut.datapotato.core :as dc]))

(defn perform-insert
  [{{:keys [atom]} dc/fixtures-visit-key}
   {:keys [ent-type visit-val]}]
  (swap! atom conj [ent-type visit-val]))

(defmacro with-fixtures
  [ent-db & body]
  `(let [ent-db# ~ent-db
         atom#   (get-in ent-db# [:fixtures :atom])]
     (reset! atom# [])
     (when-let [setup# (get-in ent-db# [:fixtures :setup])]
       (setup# nil))
     (try ~@body
          (finally (when-let [teardown# (get-in ent-db# [:fixtures :teardown])]
                     (teardown# nil))))))
