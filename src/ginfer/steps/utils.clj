(ns ginfer.steps.utils
  (:require [taoensso.timbre :as logger]
            [sepl.core :refer [->step]]
            [ginfer.attributes.utils :refer [->attribute-id get-attribute]]))

(defn ->verify-node-id-and-step
  [steps flow {:keys [node attribute] :as args}]
  (let [[type id] (clojure.string/split node #"/" 2)]
    (if (and (not (clojure.string/blank? type))
             (not (clojure.string/blank? id)))
      (->step steps flow args)
      (logger/warn (format "Malformed node id %s, intended flow %s for ?TYPE?/%s"
                           node flow attribute)))))

(defn args-s11n
  [{:keys [attribute] :as args}]
  (cond-> args
          (map? attribute) (update :attribute ->attribute-id)))

(defn args-de-s11n
  [blueprints {:keys [attribute] :as args}]
  (update args :attribute (partial get-attribute blueprints)))
