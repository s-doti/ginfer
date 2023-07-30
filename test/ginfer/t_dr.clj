(ns ginfer.t-dr
  (:require [midje.sweet :refer :all]
            [taoensso.timbre :as logger]
            [persistroids.connector :as pcon]
            [ginfer.attributes.core :refer :all]
            [ginfer.attributes.en :refer [import-blueprints]]
            [ginfer.steps.core :refer :all]
            [ginfer.utils :refer [->node-id]]
            [ginfer.core :refer :all]))

(background
  (around :facts
          (let []
            (logger/with-level :info ?form))))

(defn next-value [n-1 n-2]
  (case [n-1 n-2]
    [nil nil] 0
    [0 nil] 1
    (+ n-1 n-2)))

(defn next-id [id max]
  (let [idx (-> id ->node-id read-string inc)]
    (when (> max idx)
      (str "fibonacci/" idx))))

(def en '("fibonacci value next_value from [[n-1 value] [n-1 n-1 value]]"
           "fibonacci has n+1 and fibonacci has n-1"
           "fibonacci n+1 next_id from [[id] [env max]] on-events [boot]"))

(def product-db (atom {}))
(def operations-db (atom nil))

(defn get-crashing-connector [crash-mid-way?]
  (reify pcon/Connector
    (get-id [this] "my-connector")
    (read [this [id attribute :as args]]
      (if (and crash-mid-way? (= "fibonacci/5" id))
        (throw (Exception.))
        (get @product-db id)))
    (write [this [id attribute :as args] value] {id value})
    (flush [this writes]
      (swap! product-db (partial apply merge-with merge) writes))))

(defn fibonacci [behavior]
  (infer (import-blueprints en)
         (or @operations-db (->update [] "fibonacci/0" :fibonacci/value 0))
         :connectors [(get-crashing-connector (= "crash mid-way" behavior))]
         :env-context {:max 9}
         :writes-threshold 1
         :checkpoint-handler (fn [runtime-state]
                               (reset! operations-db (force runtime-state)))))

(midje.config/at-print-level
  :print-facts

  (fact
    "DR demonstration"

    (fibonacci "crash mid-way") => (throws Exception)

    (not-empty @product-db) => truthy
    (some? @operations-db) => truthy

    (fibonacci "resume from crash") => truthy

    (->> @product-db
         (sort-by key)
         (vals)
         (map (comp :data :value))) => [0 1 1 2 3 5 8 13 21])

  )
