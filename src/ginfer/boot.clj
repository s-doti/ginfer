(ns ginfer.boot
  (:require [clojure.core.cache.wrapped :as c]
            [ginfer.attributes.utils :refer :all]
            [ginfer.attributes.defaults :refer [get-version]]
            [ginfer.utils :refer :all]
            [ginfer.attributes.core :refer :all]
            [ginfer.utils :refer [get-time-ms collify]]
            [persistroids.core :as p]
            [persistroids.connector :as pcon]
            [ginfer.connectors.endpoints.connector :as epcon]
            [ginfer.connectors.endpoints.http :refer [->HttpConnector]]
            [ginfer.connectors.persistence.mem :refer [map->MemConnector]])
  (:import (clojure.lang Associative)))

(defn deduce-listeners [blueprints]
  (letfn [(->symmetric-attribute [attribute]
            (:symmetric-attribute (get-attribute blueprints attribute)))
          (deduce-propagation-paths [blueprints]
            (for [[target {:keys [notifiers]}] blueprints
                  path notifiers]
              (when (every? (partial get-attribute blueprints) path)
                (loop [propagation-paths []
                       [source & steps] (reverse path)]
                  (let [propagation-path (conj (mapv ->symmetric-attribute steps) target)]
                    (cond-> propagation-paths
                            (every? (comp not nil?) propagation-path) (conj [source propagation-path])
                            steps (recur steps)))))))
          (apply-listeners [blueprints [source-attribute propagation-path]]
            (update-in blueprints [source-attribute :listeners] (comp distinct conj) propagation-path))]
    (->> (deduce-propagation-paths blueprints)
         (apply concat)
         (reduce apply-listeners blueprints))))

(defn gen-boot-attributes [blueprints]
  (-> blueprints
      (assoc :id (->static))
      (assoc :type (->immutable (->on-boot (->dynamic ->node-type [[:id]]))))
      (assoc :version (->boot (->dynamic get-version [])))
      (assoc :created (-> (->dynamic get-time-ms [])
                          (->boot)
                          (->fire-event :created)))
      (assoc :modified (->on-event (->dynamic get-time-ms []) :modified))
      #_(assoc :self (->boot (->ref :self)))))

(defn get-node-types [blueprints] (distinct (keep namespace (keys blueprints))))

(defn prep-events-listeners [blueprints]
  (let [node-types (get-node-types blueprints)
        events-listeners
        (reduce #(apply update-in %1 %2)
                {}
                (for [{:keys [id on-events] :as attribute} (sort-by (comp namespace :id) (vals blueprints))
                      node-type (or (collify (namespace id)) node-types)
                      event on-events]
                  [[node-type event] conj id]))]
    (assoc blueprints
      :events-listeners events-listeners
      :node-types node-types)))

(defn fix-events [blueprints]
  (->> blueprints
       (map (fn [[id attribute]]
              (let [on-events (set (:on-events attribute))]
                [id (update attribute :notify-events (partial remove on-events))])))
       (into {})))

(defn fix-ids [blueprints]
  (->> blueprints
       (map (fn [[id attribute]] [id (assoc attribute :id id)]))
       (into {})))

(defn prep-blueprints [blueprints]
  (-> blueprints
      (gen-boot-attributes)
      (fix-ids)
      (fix-events)
      (deduce-listeners)
      (prep-events-listeners)))

(defn with-env-context [connectors env-context]
  (map #(cond-> %
                (instance? Associative %)
                (assoc :env env-context))
       connectors))

(defn prep-persistence-connectors [connectors env-context]
  (cond-> connectors
          (empty? connectors) (conj (pcon/connect (map->MemConnector {})))
          :with-env (with-env-context env-context)))

(defn prep-endpoint-connectors [connectors env-context]
  (-> connectors
      (conj (->HttpConnector))
      (with-env-context env-context)))

;todo support this in persistroids
(defn apply-cache-threshold
  ""
  [persistroids cache-threshold]
  (cond-> persistroids
          (number? cache-threshold)
          (assoc :cache
                 [nil
                  (c/fifo-cache-factory
                    {} :threshold cache-threshold)])))

(defn boot-state [blueprints
                  {:keys [connectors
                          endpoint-connectors
                          env-context
                          writes-threshold
                          millis-threshold
                          checkpoint-handler
                          cache-threshold]
                   :or   {writes-threshold 99
                          millis-threshold 1000}
                   :as   opts}]
  (let [persistroids (p/init :connectors (prep-persistence-connectors connectors env-context)
                             :checkpoint-handler checkpoint-handler
                             :writes-threshold writes-threshold
                             :millis-threshold millis-threshold
                             :init-fn init-node
                             :cache-key (fn [args] (:node args (first args))))]
    {:blueprints          (prep-blueprints blueprints)
     :endpoint-connectors (prep-endpoint-connectors endpoint-connectors env-context)
     :env-context         env-context
     :persistence         (apply-cache-threshold persistroids cache-threshold)}))