(ns ginfer.connectors.persistence.mem
  (:require [persistroids.connector :refer [Connector]]
            [ginfer.attributes.utils :refer [->attribute-id strip-ns]]))

(defrecord MemConnector [config state]

  Connector

  (get-id [this] "mem")

  (connect [this]
    (if state
      this
      (assoc this :state (atom {}))))

  (disconnect [this]
    (if (not state)
      this
      (dissoc this :state)))

  ;return the entire data per given id
  (read
    [this [id attribute]]
    (get @state id))

  ;write the entire data per given id
  (write [this [id attribute :as args] value]
    {id (select-keys value [(strip-ns (->attribute-id attribute))])})

  (flush [this writes]
    (swap! state (partial apply merge-with merge) writes)))