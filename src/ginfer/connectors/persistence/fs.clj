(ns ginfer.connectors.persistence.fs
  (:require [persistroids.connector :refer [Connector]])
  (:import (java.io File)))

;naively load/store the data in its entirety every time,
; this is strictly for demo uses

(defrecord FSConnector [config state]
  Connector

  (connect [this]
    (if state
      this
      (if-let [fs-path (:fs-path config)]
        (do
          (when (:reset? config)
            (spit fs-path {}))
          (assoc this :state {}))
        (let [fs-path (.getAbsolutePath
                        (File/createTempFile
                          "ginfer"
                          ".demo.fs"))]
          (spit fs-path {})
          (-> this
              (assoc-in [:config :fs-path] fs-path)
              (assoc :state {}))))))

  (disconnect [this]
    (if (not state)                                         ; already stopped
      this
      (dissoc this :state)))

  (get-id [this] "fs")

  ;return the entire data per given id
  (read [this [id attribute]]
    (let [fs-path (:fs-path config)
          fs-db (read-string (slurp fs-path))]
      (get fs-db id)))

  ;write the entire data per given id
  (write [this [id attribute] value]
    {id value})

  (flush [this writes]
    (let [fs-path (:fs-path config)
          fs-db (read-string (slurp fs-path))]
      (spit fs-path (apply merge-with merge fs-db writes)))))