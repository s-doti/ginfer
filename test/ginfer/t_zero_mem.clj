(ns ginfer.t-zero-mem
  (:require [midje.sweet :refer :all]
            [taoensso.timbre :as logger]
            [persistroids.connector :as connector]
            [ginfer.connectors.persistence.fs :refer [map->FSConnector]]
            [ginfer.attributes.core :refer :all]
            [ginfer.steps.core :refer :all]
            [ginfer.core :refer :all]))

;demonstrates using the fs for persistence rather than holding state in-mem

(background
  (around :facts
          (let [fs-connector (connector/connect (map->FSConnector {}))]
            (try (logger/with-level :info ?form)
                 (finally
                   (connector/disconnect fs-connector))))))

(def blueprints
  {:connects-to         (->ref :connected-from)
   :connected-from      (->ref :connects-to)
   :data-point          (generic)
   :inferred-data-point (->inferred (fnil inc 0) [[:connects-to :data-point]])})

(def events
  (-> []
      (->link "some/node" :connects-to "another/node" :connected-from)
      (->update "another/node" :data-point 2)))

(midje.config/at-print-level
  :print-facts

  (fact "no data is held in memory"
        (-> blueprints
            (infer events
                   :connectors [fs-connector]
                   :cache-threshold 1)
            (get-data "some/node" :inferred-data-point)) => 3))
