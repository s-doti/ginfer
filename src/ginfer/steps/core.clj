(ns ginfer.steps.core
  (:require [ginfer.steps.utils :refer [->verify-node-id-and-step]]
            [ginfer.attributes.utils :refer :all]))

(defn ->update
  [steps node attribute value]
  (let [args {:node      node
              :attribute attribute
              :new-value value}]
    (->verify-node-id-and-step steps :update-flow args)))

(defn ->link
  [steps source-node source-attribute target-node & [target-attribute meta link-back?]]
  (let [args {:node             source-node
              :attribute        source-attribute
              :target-node      target-node
              :target-attribute target-attribute
              :meta             meta
              :link-back?       (if (nil? link-back?) true link-back?)}]
    (->verify-node-id-and-step steps :link-flow args)))

(defn ->unlink
  [steps source-node source-attribute target-node target-attribute & [link-back?]]
  (let [args {:node             source-node
              :attribute        source-attribute
              :target-node      target-node
              :target-attribute target-attribute
              :link-back?       (if (nil? link-back?) true link-back?)}]
    (->verify-node-id-and-step steps :unlink-flow args)))

(defn ->mutate
  [steps node attribute mutate-fn {:keys [data] :as old-value} new-value version]
  (let [data' (mutate-fn data new-value)
        value (prep-attribute-value old-value version data')
        args {:node      node
              :attribute attribute
              :old-value old-value
              :new-value new-value
              :value     value}]
    (->verify-node-id-and-step steps :mutate-flow args)))

(defn ->mutate-many
  [steps node attribute mutate-fn {:keys [data] :as old-value} new-values version]
  (let [data' (reduce mutate-fn data new-values)
        value (prep-attribute-value old-value version data')
        args {:node      node
              :attribute attribute
              :old-value old-value
              :new-value new-values
              :value     value}]
    (->verify-node-id-and-step steps :mutate-flow args)))

(defn ->notify
  [steps node attribute]
  (let [args {:node node :attribute attribute}]
    (->verify-node-id-and-step steps :notify-flow args)))

(defn ->notify-event
  ([steps node attribute event]
   (->notify-event steps node attribute event nil))
  ([steps node attribute event event-data]
   (let [args {:node       node
               :attribute  attribute
               :event      event
               :event-data event-data}]
     (->verify-node-id-and-step steps :notify-event-flow args))))

(defn ->eval
  ([steps node attribute]
   (->eval steps node attribute {}))
  ([steps node attribute external-binding]
   (let [args {:node             node
               :attribute        attribute
               :external-binding external-binding}]
     (->verify-node-id-and-step steps :eval-flow args))))

(defn ->side-effect
  [steps node attribute side-effect callback-trigger-fn]
  (let [args {:node                node
              :attribute           attribute
              :side-effect         side-effect
              :callback-trigger-fn callback-trigger-fn}]
    (->verify-node-id-and-step steps :side-effect-flow args)))

(defn ->notify-events
  [steps {:keys [node attribute] :as args} curr-value events]
  (let [attribute-id (->attribute-id attribute)
        event-data {:node node
                    :attribute attribute-id}]
    (reduce #(->notify-event %1 node attribute-id %2 event-data) steps events)))