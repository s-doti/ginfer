(ns ginfer.connectors.endpoints.http
  (:require [taoensso.timbre :as logger]
            [org.httpkit.client :as http]
            [ring.util.codec :refer [url-encode]]
            [seamless-async.core :refer [scallback *async?*]]
            [ginfer.connectors.endpoints.connector :refer [Connector]]))

(defrecord HttpConnector []
  Connector

  (get-id [this] "http")

  (query [this {:keys [node attribute sources-data]}]
    (let [{:keys [format-fn url body-fn headers opts]} attribute
          non-ns-id (second (clojure.string/split node #"/" 2))
          http-request (cond-> {:url (format-fn url (url-encode non-ns-id)) :idle-timeout 5000}
                               body-fn (assoc :body (apply body-fn non-ns-id (into [] sources-data)) :method :post)
                               headers (assoc :headers headers)
                               opts (merge opts))]
      (logger/debug "query-http" (hash http-request) (:url http-request))
      (let [cb (fn [{:keys [status headers body error] :as response}]
                 (cond-> response
                         (instance? Throwable error) (assoc :error (.getMessage error))))
            http-fn (scallback http/request :callback cb)]
        (if *async?*
          (http-fn http-request :callback)
          (deref (http/request http-request cb)))))))