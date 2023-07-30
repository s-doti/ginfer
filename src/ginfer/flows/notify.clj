(ns ginfer.flows.notify
  (:require [taoensso.timbre :as logger]
            [ginfer.steps.core :refer [->eval]]
            [ginfer.attributes.utils :refer [->attribute-id]]))

(defn notify-flow-fn
  ""
  [{:keys [node attribute]} listeners & [{:keys [stack] :as metacontext}]]
  (let [steps '()]
    (reduce
      #(do (logger/debug [node (->attribute-id attribute)]
                         "-notify->" (vec %2) (count stack))
           (apply ->eval %1 %2)) steps listeners)))

(defn notify-event-flow-fn
  ""
  [{:keys [node attribute event event-data]} listeners & [{:keys [stack] :as metacontext}]]
  (let [steps '()]
    (reduce
      (fn [steps [n att]]
        (logger/debug [node (->attribute-id attribute)]
                      "-notify-" event "->" [n att] (count stack))
        (->eval steps n att event-data))
      steps
      listeners)))