(ns ginfer.flows.core
  (:require [taoensso.timbre :as logger]
            [ginfer.flows.update :refer [update-flow-fn link-flow-fn unlink-flow-fn]]
            [ginfer.flows.notify :refer [notify-flow-fn notify-event-flow-fn]]
            [ginfer.flows.eval :refer [eval-flow-fn]]
            [ginfer.side-effects.core :refer :all]
            [ginfer.attributes.utils :refer [->attribute-id]]))

(def core-flows
  {
   ;update flows => mutate, notify flows
   :update-flow       {:side-effect-fn get!
                       :process-fn     update-flow-fn}
   :link-flow         {:side-effect-fn get!
                       :process-fn     link-flow-fn}
   :unlink-flow       {:side-effect-fn get!
                       :process-fn     unlink-flow-fn}

   ;mutate - side-effect -only flow
   :mutate-flow       {:side-effect-fn update!}

   ;notify flows => eval flows
   :notify-flow       {:side-effect-fn get-listeners!
                       :process-fn     notify-flow-fn}
   :notify-event-flow {:side-effect-fn get-event-listeners
                       :process-fn     notify-event-flow-fn}

   ;eval flows => update flows
   :eval-flow         {:side-effect-fn get-sources-data!
                       :process-fn     eval-flow-fn}
   :side-effect-flow  {:side-effect-fn (fn [_ {:keys [attribute side-effect]}]
                                         (let [{:keys [async? derefable? timeout-ms timeout-val]} attribute]
                                           (if derefable?
                                             (if (and timeout-ms timeout-val)
                                               (deref side-effect timeout-ms timeout-val)
                                               (deref side-effect))
                                             side-effect)))
                       :process-fn     (fn side-effect-flow-fn
                                         [{:keys [node attribute callback-trigger-fn]}
                                          side-effect-outcome
                                          & [{:keys [stack] :as context}]]
                                         (logger/info [node (->attribute-id attribute)]
                                                      "-side-effect->" (type side-effect-outcome) (count stack))
                                         (callback-trigger-fn side-effect-outcome))}})