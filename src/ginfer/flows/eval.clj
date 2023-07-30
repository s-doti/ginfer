(ns ginfer.flows.eval
  (:require [clojure.set :as set]
            [taoensso.timbre :as logger]
            [ginfer.side-effects.utils :refer [uninitialized? uninit-key]]
            [ginfer.steps.core :refer :all]
            [ginfer.attributes.utils :refer :all]
            [ginfer.utils :refer [collify]]))

(defn- extract-uninitialized
  "Recursively extract uninitialized sources-data"
  [sources-data]
  (not-empty
    (filter some?
            (mapcat #(cond
                       (map? %) [(uninit-key %)]
                       (sequential? %) (extract-uninitialized %))
                    sources-data))))

(defn- nullify-uninitialized
  "Recursively convert uninitialized sources-data into nil"
  [sources-data]
  (map #(cond
          (uninitialized? %) nil
          (sequential? %) (nullify-uninitialized %)
          :always %)
       sources-data))

;todo revisit stateful mechanism to control 'propagation distance' from epi-center
(def propagation-distance (atom 1e6))

(defn- handle-side-effect
  "Experimental flow using async user-provided logic plugin"
  [node attribute-id eval-fn sources-data]
  (let [side-effect (apply eval-fn sources-data)]
    (->side-effect '() node attribute-id side-effect
                   #(->eval '()
                            node
                            attribute-id
                            {:side-effect-outcome %}))))

(defn- acquire-outcome
  "Invoke user-provided logic plugin with sources-data to acquire outcome"
  [{:keys [node attribute external-binding]}
   {:keys [sources-data]}]
  (let [{:keys [eval-fn]} attribute
        attribute-id (->attribute-id attribute)
        side-effect-outcome? (contains? external-binding :side-effect-outcome)]
    (if side-effect-outcome?
      (:side-effect-outcome external-binding)
      (try
        (apply eval-fn sources-data)
        (catch Throwable t
          (throw
            (Exception.
              (format "eval-flow failed: [%s %s] eval-fn %s with args %s"
                      node attribute-id eval-fn (into [] sources-data))
              t)))))))

(defn- diff [current outcome]
  (set/difference (set (map (comp :id val) current))
                  (set outcome)))

(defn- call-unlink
  [node attribute-id symmetric-attribute]
  (partial reduce
           #(->unlink %1 node attribute-id %2 symmetric-attribute)))

(defn- call-link
  [steps target-node link-back?
   {:keys [node attribute external-binding]}
   {:keys [sources-data]}]
  (let [{:keys [meta-fn symmetric-attribute]} attribute
        attribute-id (->attribute-id attribute)]
    (->link steps
            target-node
            symmetric-attribute
            node
            attribute-id
            (meta-fn {:source-node      node
                      :source-attribute attribute-id
                      :target-node      target-node
                      :target-attribute symmetric-attribute
                      :external-binding external-binding
                      :sources-data     sources-data})
            link-back?)))

(defn- handle-ref
  "Subflow of eval specially for refs, calling the link api rather than update.
   This logic isn't additive."
  [{:keys [node attribute] :as args}
   {:keys [current] :as current-value}
   outcome]
  (if-let [target-nodes (not-empty (collify outcome))]

    ;acquired some refs to link to
    (let [{:keys [type symmetric-attribute]} attribute
          attribute-id (->attribute-id attribute)]
      (if (= type :ref)

        ;single ref case - both sides and unlinking are handled
        (call-link '() outcome true args current-value)

        (let [to-unlink (diff current outcome)
              unlink-multi (call-unlink node attribute-id symmetric-attribute)]

          ;multi refs case - may require some unlinking in the process
          (-> #(call-link %1 %2 false args current-value)
              (reduce '() target-nodes)                     ;link from other side + with meta
              (->link node attribute-id target-nodes symmetric-attribute nil false) ;link from this side (notify once)
              (unlink-multi to-unlink)))))                  ;unlink if needed

    ;complete and total unlink all existing refs
    (->unlink '() node (->attribute-id attribute) nil (:symmetric-attribute attribute))))

(defn- eval
  "Evaluate current location in the graph (denoted by [node attribute])"
  [{:keys [node attribute external-binding] :as args}
   {:keys [sources-data] :as current-value}
   & [depth]]

  (let [{:keys [eval-fn async? derefable?]} attribute
        attribute-id (->attribute-id attribute)
        side-effect-outcome? (contains? external-binding :side-effect-outcome)
        ref? (ref? attribute)]

    (if (and (or async? derefable?) (not side-effect-outcome?))
      (handle-side-effect node attribute-id eval-fn sources-data)

      (let [outcome (acquire-outcome args current-value)]
        (logger/debug [node attribute-id] "-eval->" (type outcome) depth)

        (if-not ref?
          (->update '() node attribute-id outcome)
          (handle-ref args current-value outcome))))))

(defn- attempt-eval-uninit-deps
  "Require each uninit source be evaluated before reevaluating current"
  [{:keys [node attribute]} uninitialized]
  (->eval (reduce #(->eval %1 (:node %2) (:attribute %2))
                  '()
                  (distinct uninitialized))
          node
          attribute
          {:uninit uninitialized}))

(defn- step-repeats?
  "Examine key fields of step to determine identity"
  [{:keys [node attribute]} step & [prev-uninit uninit]]
  (let [{{n :node att :attribute} :args t :type} step]
    (= [t n att prev-uninit]
       [:eval-flow node (->attribute-id attribute) uninit])))

(defn- handle-uninitialized-sources
  "Require each uninit source be evaluated before reevaluating current.
   Keep attempting as long as progress shown, and resort to nil if no progress"
  [{:keys [node attribute] :as args}
   {:keys [] :as current-value}
   prev-uninit uninit stack]

  (if (step-repeats? args (peek stack))

    (if (not= prev-uninit uninit)
      (attempt-eval-uninit-deps args uninit)

      ;all uninit sources attempted eval but not all succeeded, resort to nil
      (eval args (update current-value :sources-data nullify-uninitialized) (count stack)))

    (when-not (some (partial step-repeats? node attribute) stack)
      (attempt-eval-uninit-deps args uninit))))

(defn eval-flow-fn
  [{:keys [node attribute external-binding] :as args}
   {:keys [sources-data] :as current-value}
   & [{:keys [stack] :as metacontext}]]

  ;todo revisit this mechanism and reconsider its worth
  ;cap propagation distance as specified
  (if (< @propagation-distance (count (distinct (map (comp :node :args) stack))))
    (logger/debug "Propagation exceeded" @propagation-distance
                  "hops from the source, at" [node (->attribute-id attribute)])

    (let [uninit (extract-uninitialized sources-data)
          prev-uninit (:uninit external-binding)]

      ;todo revisit this mechanism and reconsider its worth
      ;cap looping behavior, and be visible if it occurs (experimental)
      (if (<= 3 (count (filter #(step-repeats? node attribute % prev-uninit uninit) (rest stack))))
        (logger/warn "Propagation reached" [node (->attribute-id attribute)] "for the 3rd time")

        ;check readiness to perform eval
        (let [{:keys [eval-on-condition]} attribute]
          (when (eval-on-condition sources-data)

            ;are all source dependencies ready for eval?
            (if (empty? uninit)
              (eval args current-value (count stack))

              ;require uninit sources attempt evaluation first
              (handle-uninitialized-sources args current-value prev-uninit uninit stack))))))))