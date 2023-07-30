(ns ginfer.connectors.endpoints.connector)

(defprotocol Connector
  (get-id [connector])
  (query [connector args]))
