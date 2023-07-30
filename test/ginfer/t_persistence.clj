(ns ginfer.t-persistence
  (:require [midje.sweet :refer :all]
            [taoensso.timbre :as logger]
            [clojure.core.async :refer [go]]
            [persistroids.connector :as pcon]
            [ginfer.attributes.core :refer :all]
            [ginfer.steps.core :refer :all]
            [ginfer.core :refer :all]))

(background
  (around :facts
          (let []
            (logger/with-level :info ?form))))

(declare proof-of-query)

(defrecord MyConnector []
  pcon/Connector
  (get-id [this] "my-connector")
  (read [this [id attribute]]
    {attribute (get-in this [:env :some-env-var])}))

(midje.config/at-print-level
  :print-facts

  (fact
    "persistence connector into external db"

    (let [db (atom {})
          blueprints {:data-point          (generic)
                      :inferred-data-point (->inferred inc [[:data-point]])}
          events (->update [] "some/node" :data-point 2)
          persistence-connector (reify pcon/Connector
                                  (get-id [this] "my-connector")
                                  (read [this [id attribute :as args]] (get @db id))
                                  (write [this [id attribute :as args] value]
                                    (swap! db merge {id value})
                                    nil))
          final-state (infer blueprints events
                             :connectors [persistence-connector])]

      (get-in @db ["some/node" :inferred-data-point :data]) => 3))

  (fact
    "persistence connector directly accessing env vars"

    (let [blueprints {:data-point (generic)}
          events (->notify [] "some/node" :data-point)
          env {:some-env-var 1}
          persistence-connector (->MyConnector)
          final-state (infer blueprints events
                             :env-context env
                             :connectors [persistence-connector])]

      ;my-connector accesses env-var in its code
      (get-data final-state "some/node" :data-point) => 1))

  (fact
    "persistence connector may be async"

    (let [db (atom {})
          blueprints {:data-point          (generic)
                      :inferred-data-point (->inferred inc [[:data-point]])}
          events (->update [] "some/node" :data-point 2)
          persistence-connector (reify pcon/Connector
                                  (get-id [this] "my-connector")
                                  (read [this [id attribute :as args]] (go (get @db id)))
                                  (write [this [id attribute :as args] value]
                                    (swap! db merge {id value})
                                    nil))
          final-state (infer blueprints events
                             :connectors [persistence-connector])]

      (get-in @db ["some/node" :inferred-data-point :data]) => 3))

  )
