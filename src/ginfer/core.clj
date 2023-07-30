(ns ginfer.core
  (:require [seamless-async.core :refer [set-*async?*]]
            [persistroids.core :as p]
            [sepl.core :as sepl]
            [ginfer.flows.core :refer [core-flows]]
            [ginfer.steps.utils :refer [args-s11n args-de-s11n]]
            [ginfer.attributes.utils :refer [->attribute-id]]
            [ginfer.attributes.en :refer [import-blueprints]]
            [ginfer.steps.en :refer [analyze-events]]
            [ginfer.side-effects.core :refer [get! get-refs]]
            [ginfer.side-effects.optimizer :refer [side-effects-optimizer]]
            [ginfer.boot :refer [boot-state]]))

(def get-data
  "Takes state, node and attribute, and returns any graph
   data at coordinate [node attribute]."
  (comp :data get!))

(def get-refids
  "Takes state, node and attribute, and returns any graph
   reference ids data at coordinate [node ref-attribute]."
  get-refs)

(defn- infer-now
  "Runs inference given graph blueprints and events.
   Optional opts:
   :connectors - a list of stateful objects satisfying the persistroids.connectors/Connector
                 protocol. (default: persistroids.connectors.mem/MemConnector)
   :endpoint-connectors - a list of stateful objects satisfying the ginfer.connectors.endpoints.connector/Connector
                          protocol. (default: ginfer.connectors.endpoints.http/HttpConnector)
   :env-context - a (nested) map of env vars accessible during inference.
   :dialect - indicate blueprints+events are provided in natural language (\"en\")
   :writes-threshold - indicate a cumulative threshold for flushing buffered writes. (default:99)
   :millis-threshold - indicate a time threshold for flushing buffered writes. (default:99)
   :cache-threshold - set a maximum size per internal caching.
   :checkpoint-handler - logic to execute in correlation with data flushing, which takes the
                         internal runtime state and persists it for later recovery. (advanced usage)
   :io - an atom, for debug support. (advanced usage)"
  [sepl-fn blueprints steps & [{:keys [io dialect] :as opts}]]
  (let [blueprints (cond->> blueprints
                            (= "en" dialect) (import-blueprints))
        steps (cond->> steps
                       (= "en" dialect) (analyze-events blueprints))
        {:keys [blueprints] :as state} (boot-state blueprints opts)]
    (sepl-fn core-flows state steps
             :->ser-args args-s11n
             :->deser-args (partial args-de-s11n blueprints)
             :with-optimizer side-effects-optimizer
             :with-checkpoint (fn [state checkpoint]
                                (assoc-in state [:persistence :checkpoint] checkpoint))
             :max-iterations (Integer/MAX_VALUE)
             :io io)))

(defn finalize
  "Shuts down internal stateful objects properly, in the case of async and/or lazy flavours of
   execution. (i.e. async-infer, lazy-infer, and lazy-async-infer)"
  [{:keys [persistence]}]
  (p/shutdown (dissoc persistence :checkpoint)))

(defn infer
  "Runs inference given graph blueprints and events.
   Returns final state once inference is done."
  [blueprints events & {:as opts}]
  (set-*async?* false)
  (let [outcome (infer-now sepl/sepl blueprints events opts)]
    (finalize outcome)
    outcome))

(defn async-infer
  "Runs inference given graph blueprints and events.
   Returns an async channel to serve final state once inference is done."
  [blueprints events & {:as opts}]
  (set-*async?* true)
  (infer-now sepl/sepl blueprints events opts))

(defn lazy-infer
  "Runs inference given graph blueprints and events.
   Returns a lazy sequence from which inference steps are pulled."
  [blueprints events & {:as opts}]
  (infer-now sepl/lazy-sepl blueprints events opts))

(defn lazy-async-infer
  "Runs inference given graph blueprints and events.
   Returns an async channel from which inference steps are pulled."
  [blueprints events & {:as opts}]
  (infer-now sepl/async-sepl blueprints events opts))