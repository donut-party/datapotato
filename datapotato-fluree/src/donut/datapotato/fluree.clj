(ns donut.datapotato.fluree
  (:require
   [donut.datapotato.core :as dc]
   [fluree.db.api :as fdb]))

(defn insert
  [{{:keys [connection]} dc/fixtures-visit-key
    :as                  ent-db}
   {:keys [ent-name visit-val]}]

  (let [{:keys [conn ledger]} connection
        collection            (get-in (dc/ent-schema ent-db ent-name) [dc/fixtures-visit-key :collection])
        result                @(fdb/transact conn ledger [(assoc visit-val :_id collection)])]
    (assoc visit-val :_id (let [tempids (get-in result [:tempids collection])]
                            (if (vector? tempids)
                              (first tempids)
                              tempids)))))


(in-ns 'fluree.db.api)
(require
 '[fluree.crypto :as crypto]
 '[fluree.db.operations :as ops]
 '[fluree.db.util.core :as util]
 '[fluree.db.util.json :as json])
(defn delete-ledger-async
  "Completely deletes a ledger.
  Returns a channel that will receive a boolean indicating success or failure.
  A 200 status indicates the deletion has been successfully initiated.
  The full deletion happens in the background on the respective ledger.
  Query servers get notified when this process initiates, and ledger will be marked as
  being in a deletion state during the deletion process.
  Attempts to use a ledger in a deletion state will throw an exception."
  ([conn ledger] (delete-ledger-async conn ledger))
  ([conn ledger opts]
   (try (let [{:keys [nonce expire timeout private-key] :or {timeout 60000}} opts
              timestamp (System/currentTimeMillis)
              nonce     (or nonce timestamp)
              expire    (or expire (+ timestamp 30000)) ;; 5 min default
              cmd-data  {:type   :delete-ledger
                         :ledger ledger
                         :nonce  nonce
                         :expire expire}]
          (if private-key
            (let [cmd          (-> cmd-data
                                   (util/without-nils)
                                   (json/stringify))
                  sig          (crypto/sign-message cmd private-key)
                  persisted-id (submit-command-async conn {:cmd cmd
                                                           :sig sig})]
              persisted-id)
            (ops/unsigned-command-async conn cmd-data)))
        (catch Exception e e))))
