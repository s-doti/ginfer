(ns ginfer.test.core
  (:require [midje.sweet :refer :all]
            [taoensso.timbre :as logger]
            [persistroids.connector :as connector]
            [ginfer.connectors.persistence.mem :refer [map->MemConnector]]
            [ginfer.steps.core :refer :all]
            [ginfer.core :refer :all]))

(background
  (around :facts
          (let []
            (logger/with-level :info ?form))))

(defn generate-node-id
  "Produce flavors of node ids:
  'ns/id' or 'some/id' for test output/input nodes
  'ns/node-id' or 'some/node-id' for anonymous nodes"
  [ns & [id]]
  (str (or ns "some") \/ (or id "node-id")))

(defn generate-mocking-events [blueprints attribute inputs]
  (let [{:keys [sources]} (get blueprints attribute)
        entity-type (some namespace (cons attribute (map first sources)))
        node-id (generate-node-id entity-type "output")]
    (loop [src-node node-id
           [[hop next-hop :as path] & rest-paths] sources
           events []
           i 0]
      (let [hops (count path)]
        (case hops
          0 (if rest-paths
              (recur node-id rest-paths events (inc i))
              events)
          1 (let [events (->update events src-node hop (nth inputs i))]
              (if rest-paths
                (recur node-id rest-paths events (inc i))
                events))
          (let [symmetric-hop (:symmetric-attribute (get blueprints hop))
                entity-type (some namespace [symmetric-hop next-hop])
                dst-node (generate-node-id entity-type (when (= 2 hops) (str "input-" i)))
                events (->link events src-node hop dst-node symmetric-hop)]
            (recur dst-node (cons (rest path) rest-paths) events i)))))))

(defn disable-endpoints
  "Returns blueprints where all endpoints are turned
   static (ie made to be non-evaluating)."
  [blueprints]
  (->> blueprints
       (map (fn [[k v]]
              [k (cond-> v
                         (= :endpoint (:type v))
                         (assoc :eval-on-condition (constantly false)))]))
       (into {})))

(defn lock
  "Returns blueprints where all given attributes are turned
   static (ie made to be non-evaluating)."
  [blueprints attributes]
  (reduce #(update %1 %2 assoc :eval-on-condition (constantly false))
          blueprints
          attributes))

(defn- get-mocked [events]
  (->> events
       (filter #(= :update-flow (:type %)))
       (map #(get-in % [:args :attribute]))))

(defn init-mock [blueprints attribute inputs mem-connector]
  (let [events (generate-mocking-events blueprints attribute inputs)
        mocked (get-mocked events)]
    (-> blueprints
        (disable-endpoints)
        (lock mocked)
        (infer events :connectors [mem-connector]))
    mocked))

(defn verify-use-case
  [blueprints events attribute [input-sources-data expected]]
  (fact
    (let [output-node (generate-node-id (namespace attribute) "output")
          mem-connector (connector/connect (map->MemConnector {}))]
      (let [mocked (init-mock blueprints attribute input-sources-data mem-connector)
            actual (-> blueprints
                       (disable-endpoints)
                       (lock mocked)
                       (infer events :connectors [mem-connector])
                       (get-data output-node attribute))]
        actual))
    => expected))

(defn get-notifiers [blueprints attribute]
  (let [sources (get-in blueprints [attribute :sources])]
    (->> sources
         (map-indexed (fn [idx path]
                        (let [last-hop (last path)
                              prefix (namespace last-hop)]
                          (if (= 1 (count path))
                            [(generate-node-id prefix "output") last-hop]
                            [(generate-node-id prefix (str "input-" idx)) last-hop])))))))

(defn verify-attribute
  [blueprints attribute use-cases]
  (doseq [i (range (count use-cases))
          :let [output-node (generate-node-id (namespace attribute) "output")
                use-case (nth use-cases i)]]

    ;eval
    (logger/info "eval" output-node attribute "per use-case" i)
    (verify-use-case blueprints
                     (->eval [] output-node attribute)
                     attribute
                     use-case)

    ;notify
    (doseq [[notifying-node notifying-attribute] (get-notifiers blueprints attribute)]
      (logger/info "notify" notifying-node notifying-attribute "per use-case" i)
      (verify-use-case blueprints
                       (->notify [] notifying-node notifying-attribute)
                       attribute
                       use-case))))
