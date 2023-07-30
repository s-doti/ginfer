(ns ginfer.side-effects.core
  (:require [taoensso.timbre :as logger]
            [seamless-async.core :refer :all]
            [ginfer.utils :refer :all]
            [ginfer.side-effects.utils :refer :all]
            [ginfer.utils :refer [ifnil]]
            [persistroids.core :as p]
            [ginfer.attributes.utils :refer :all]))

(defn get!
  ""
  ([state {:keys [node attribute]}]
   (get! state node attribute))
  ([{:keys [blueprints persistence]} node attribute]
   (let [{:keys [ttl-fn]} (get-attribute blueprints attribute)
         attribute-id (->attribute-id attribute)]
     (scond-> (p/read persistence [node attribute-id])
              attribute (lookup-attribute-value (strip-ns attribute-id) ttl-fn)))))

(defn update!
  ""
  [state {:keys [node attribute value]}]
  (let [attribute-id (->attribute-id attribute)
        node' (s-> (get! state node nil)
                   (assoc (strip-ns attribute-id) value))
        return-state (fn [node'] state)]
    (logger/debug [node attribute-id] "-mutate->" (clojure.core/type value))
    (s->> node'
          (p/write (:persistence state) [node attribute])
          (return-state))))

(defn get-refs
  ""
  [{:keys [blueprints] :as state} node attribute]
  (s->> (get! state node attribute)
        (handle-return-value blueprints
                             node
                             attribute
                             as-ref-ids)))

(defn resolve!
  ""
  [state node bindings paths f]
  (letfn [(stepper! [x bindings path]
            (when x
              (if (uninitialized? x)
                x
                (if (coll? x)
                  (smap #(getter! % bindings path) x)
                  (getter! x bindings path)))))
          (getter! [n bindings [step & steps :as path]]
            (if (uninitialized? n)
              n
              (let [bindings (assoc bindings [:id] n)]
                (if (contains? bindings path)
                  (get bindings path)
                  (if steps
                    (s-> (get-refs state n step)
                         (stepper! bindings steps))
                    (f n step))))))]
    (smap (partial getter! node bindings) paths)))

(defn get-listeners!
  ""
  [state {node :node {:keys [listeners]} :attribute}]
  (s->> (resolve! state node {} listeners #(do [%1 %2]))
        (flatten)
        (remove uninit-key)
        (filter some?)
        (partition-all 2)))

(defn get-sources-data!
  "Retrieves data from source paths as needed,
   and/or invokes tne endpoint-connector in the case of an endpoint attribute."

  [{:keys [blueprints env-context] :as state}
   {:keys [node attribute external-bindings] :as args}]

  (let [{:keys [sources type]} attribute

        ;; Attempt to acquire the data per 'this' if previously calculated
        data (s-> (get external-bindings [:this])
                  (ifnil #(get! state args))
                  (:data))

        ;; Auxiliary fn to acquire data from source paths
        get-sources-data (fn get-sources-data [binding+this]
                           (resolve! state node binding+this sources
                                     #(s->> (get! state %1 %2)
                                            (handle-return-value blueprints %1 %2))))
        sources-data (s->> data
                           (assoc external-bindings [:this])
                           (prep-external-binding sources env-context)
                           (get-sources-data))]
    (if (= :endpoint type)
      (s->> data
            (get-endpoint-data state args sources-data)
            (assoc {} :sources-data))
      (s-> {}
           (assoc :sources-data sources-data)
           (assoc :current data)))))

(defn get-event-listeners
  ""
  [{{:keys [events-listeners]} :blueprints} {:keys [node event]}]
  (when-let [node-type (->node-type node)]
    (map (partial vector node) (get-in events-listeners [node-type event]))))
