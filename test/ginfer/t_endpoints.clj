(ns ginfer.t-endpoints
  (:require [midje.sweet :refer :all]
            [clojure.core.async :refer [go]]
            [taoensso.timbre :as logger]
            [ginfer.connectors.endpoints.connector :as epcon]
            [ginfer.attributes.core :refer :all]
            [ginfer.steps.core :refer :all]
            [ginfer.core :refer :all]))

(background
  (around :facts
          (let []
            (logger/with-level :info ?form))))

(declare proof-of-query)

(defrecord MyEPConnector []
  epcon/Connector
  (get-id [this] "my-endpoint-connector")
  (query [this {:keys [node attribute]}]
    (if (get-in this [:env :enabled])
      (proof-of-query node))))

(midje.config/at-print-level
  :print-facts

  (fact
    "injected endpoints are supported"

    (let [endpoint-connector (reify epcon/Connector
                               (get-id [this] "my-endpoint-connector")
                               (query [this {:keys [node attribute sources-data] :as args}]
                                 (proof-of-query [node (:attribute-args attribute)])))
          blueprints {:some/endpoint (->endpoint "my-endpoint-connector" :attribute-args "anything")}
          events (->eval [] "some/node" :some/endpoint)]

      (get-data
        (infer blueprints events :endpoint-connectors [endpoint-connector])
        "some/node" :some/endpoint) => "200 OK"

      (provided
        (proof-of-query ["some/node" "anything"]) => "200 OK" :times 1)))

  (fact
    "injected endpoints are eval'd"

    (let [endpoint-connector (reify epcon/Connector
                               (get-id [this] "my-endpoint-connector")
                               (query [this {:keys [node] :as args}]
                                 (proof-of-query node)))
          blueprints {:some/endpoint  (->endpoint "my-endpoint-connector")
                      :some/attribute (->dynamic inc [[:some/endpoint]])}
          events (->eval [] "some/node" :some/attribute)]

      (get-data
        (infer blueprints events :endpoint-connectors [endpoint-connector])
        "some/node" :some/attribute) => 3

      (provided
        (proof-of-query "some/node") => 2 :times 1)))

  (fact
    "injected endpoints are notified"

    (let [endpoint-connector (reify epcon/Connector
                               (get-id [this] "my-endpoint-connector")
                               (query [this {:keys [node attribute sources-data] :as args}]
                                 (proof-of-query node (into [] sources-data))))
          blueprints {:some/data-point (generic)
                      :some/endpoint   (->endpoint "my-endpoint-connector"
                                                   :sources [[:some/data-point]]
                                                   :notifiers [[:some/data-point]])}
          events (->update [] "some/node" :some/data-point 2)]

      (get-data
        (infer blueprints events :endpoint-connectors [endpoint-connector])
        "some/node" :some/endpoint) => "200 OK"

      (provided
        (proof-of-query "some/node" [2]) => "200 OK" :times 1)))

  (fact
    "endpoint enabled via env vars"

    (let [endpoint-connector (->MyEPConnector)
          blueprints {:some/endpoint (->endpoint "my-endpoint-connector")}
          events (->eval [] "some/node" :some/endpoint)
          env {:enabled true}]

      (get-data
        (infer blueprints events
               :env-context env
               :endpoint-connectors [endpoint-connector])
        "some/node" :some/endpoint) => "200 OK"

      (provided
        (proof-of-query "some/node") => "200 OK" :times 1)))

  (fact
    "endpoint disabled via env vars"

    (let [endpoint-connector (->MyEPConnector)
          blueprints {:some/endpoint (->endpoint "my-endpoint-connector")}
          events (->eval [] "some/node" :some/endpoint)
          env {:enabled false}]

      (get-data
        (infer blueprints events
               :env-context env
               :endpoint-connectors [endpoint-connector])
        "some/node" :some/endpoint) => nil

      (provided
        (proof-of-query "some/node") => "200 OK" :times 0)))

  (fact
    "injected endpoints may be async"

    (let [endpoint-connector (reify epcon/Connector
                               (get-id [this] "my-endpoint-connector")
                               (query [this {:keys [node attribute sources-data] :as args}]
                                 (proof-of-query [node (:attribute-args attribute)])))
          blueprints {:some/endpoint (->endpoint "my-endpoint-connector" :attribute-args "anything")}
          events (->eval [] "some/node" :some/endpoint)]

      (get-data
        (infer blueprints events :endpoint-connectors [endpoint-connector])
        "some/node" :some/endpoint) => "200 OK"

      (provided
        (proof-of-query ["some/node" "anything"]) => (go "200 OK") :times 1)))

  #_(fact
    "'real world' endpoints example (comment out, this is not a proper unit test)"

    (let [blueprints {:some/endpoint (->endpoint "https://www.google.com/search?q=%s")}
          events (->eval [] "some/node" :some/endpoint)]

      (:status (get-data
                 (infer blueprints events)
                 "some/node" :some/endpoint)) => 200))

  )
