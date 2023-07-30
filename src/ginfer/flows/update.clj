(ns ginfer.flows.update
  (:require [taoensso.timbre :as logger]
            [ginfer.steps.core :refer :all]
            [ginfer.attributes.utils :refer :all]
            [ginfer.attributes.defaults :refer [default-unset-fn]]
            [ginfer.utils :refer [collify]]))

(defn- diff
  "Return truthy when the current vs new values differ,
   by invoking the attribute's diff-fn on the values."
  [{:keys [diff-fn] :as attribute} current new]
  (diff-fn current new))

(defn update-flow-fn
  "If new value is different than current, then
   call mutate and notify flows."
  [{:keys [node attribute new-value] :as args}
   {:keys [data] :as curr-value}
   & [{:keys [stack] :as metacontext}]]

  (let [diff (diff attribute data new-value)
        uninitialized? (nil? curr-value)]

    ;when current and new values differ, or not initialized yet
    (when (or diff uninitialized?)
      (let [{:keys [set-fn version notify-on-condition notify-events on-events]} attribute
            attribute-id (->attribute-id attribute)
            on-boot? (some #{:boot} on-events)
            fire-boot-event? (and uninitialized? (not on-boot?))
            notify? (notify-on-condition new-value)
            next-steps '()]

        (logger/debug [node attribute-id] "-update->" (type new-value) (count stack))

        (cond-> next-steps
                :mutate (->mutate node attribute-id set-fn curr-value new-value version)
                fire-boot-event? (->notify-event node attribute-id :boot {})
                notify? (->notify node attribute-id)
                :notify-events (->notify-events args curr-value notify-events))))))

(defn- analyze-ref
  "Produce a rich model having relevant ref data for subsequent calculations."
  [target-node target-attribute meta data type]
  (let [ref (gen-ref target-node target-attribute meta)
        curr-ref (lookup-ref data type ref)
        same-ref? (= (->ref-id ref) (->ref-id curr-ref))]
    {:target-node target-node
     :ref         ref
     :curr-ref    curr-ref
     :same-ref?   same-ref?
     :new-ref     (if same-ref?
                    (merge curr-ref (dissoc ref :created))
                    ref)}))

(defn- analyze-refs
  "Returns a collection of analyzed refs having diffs from current state."
  [{:keys [attribute target-node target-attribute meta]} data]
  (let [{:keys [diff-fn type]} attribute]
    (->> (distinct (collify target-node))
         (map #(analyze-ref % target-attribute meta data type))
         (filter (comp (partial apply diff-fn)
                       (juxt :curr-ref :new-ref))))))

(defn- link-back
  "Call link flow from each target-node to current."
  [steps {:keys [node attribute target-attribute]} depth target-nodes]
  (let [attribute-id (->attribute-id attribute)]
    (reduce
      (fn [steps target-node]
        (logger/debug [node attribute-id] "-link->" [target-node target-attribute] depth)
        (->link steps target-node target-attribute node attribute-id {} false))
      steps
      target-nodes)))

(defn link-flow-fn
  "If provided ref(s) represent any diff from current state, then
   call link (back), mutate and notify flows.
   This logic is additive."
  [{:keys [node attribute link-back?] :as args}
   {:keys [data] :as curr-value}
   & [{:keys [stack] :as metacontext}]]

  ;determine whether there are any diffs in refs
  (when-let [refs-analysis (not-empty (analyze-refs args data))]

    (let [{:keys [set-fn type version notify-events on-events]} attribute
          attribute-id (->attribute-id attribute)

          target-nodes (map :target-node refs-analysis)
          refs (map :new-ref refs-analysis)
          [{:keys [curr-ref same-ref?]}] refs-analysis
          {:keys [id symmetric-attribute]} curr-ref
          unlink-prev-ref? (and (= type :ref) curr-ref (not same-ref?))

          uninitialized? (nil? curr-value)
          on-boot? (some #{:boot} on-events)
          fire-boot-event? (and uninitialized? (not on-boot?))]

      (cond-> '()
              unlink-prev-ref? (->unlink id symmetric-attribute node attribute-id)
              :link-this-side (->mutate-many node attribute-id set-fn curr-value refs version)
              link-back? (link-back args (count stack) target-nodes)
              fire-boot-event? (->notify-event node attribute-id :boot {[:id] node})
              :notify (->notify node attribute-id)
              :notify-events (->notify-events args curr-value notify-events)))))

(defn- unlink-back
  "Call unlink flow from each target-node to current."
  [steps {:keys [node attribute target-attribute]} data single? depth]
  (let [attribute-id (->attribute-id attribute)]
    (if single?
      (do (logger/debug [node attribute-id] "-unlink->" [(:id data) target-attribute] depth)
          (->unlink steps (:id data) target-attribute node attribute-id false))
      (do (logger/debug [node attribute-id] "-unlink->" ["*" target-attribute] depth)
          (reduce
            #(->unlink %1 %2 target-attribute node attribute-id false)
            steps
            (map :id (vals data)))))))

(defn unlink-flow-fn
  "If no ref(s) provided, completely unlink all current refs. If provided
   ref(s) are found in current state, then call mutate, unlink (back), and notify flows."
  [{:keys [node attribute target-node target-attribute link-back?] :as args}
   {:keys [data] :as curr-value}
   & [{:keys [stack] :as metacontext}]]

  (let [{:keys [unset-fn type version notify-events]} attribute
        attribute-id (->attribute-id attribute)
        unlinks (if (and (nil? target-node) (some? data))

                  ;complete and total unlink of all existing refs
                  (cond-> '()
                          :unlink-this-side (->mutate node attribute-id default-unset-fn curr-value nil version)
                          link-back? (unlink-back args data (= :ref type) (count stack)))

                  ;surgical unlink of a single ref (multi unlink not covered)
                  (when-let [ref (lookup-ref data type target-node target-attribute)]
                    (logger/debug [node attribute-id] "-unlink->" [target-node target-attribute] (count stack))
                    (cond-> '()
                            :unlink-this-side (->mutate node attribute-id unset-fn curr-value (->ref-id ref) version)
                            link-back? (->unlink target-node target-attribute node attribute-id false))))]

    ;handle notification as needed
    (when (not-empty unlinks)
      (cond-> unlinks
              :notify (->notify node attribute-id)
              :notify-events (->notify-events args curr-value notify-events)))))