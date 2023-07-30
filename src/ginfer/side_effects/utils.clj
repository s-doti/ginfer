(ns ginfer.side-effects.utils
  (:require [seamless-async.core :refer [s->>]]
            [ginfer.attributes.utils :refer :all]
            [ginfer.connectors.endpoints.connector :as endpoint-connector]))

(defn attribute->path
  "Convert attribute keyword to a list of strings, eg :some/attribute => ['some' 'attribute']"
  [attribute]
  (map keyword (clojure.string/split (name attribute) #"\.")))

(defn handle-attribute-value
  "If the value is properly initialized, return it, or nil if it is ttl'd;
   If the value is nil, return nil;
   If the value isn't properly initialized, wrap it proper"

  [value & [ttl-fn]]

  (if (and (map? value)
           (= #{:version :created :modified :data} (set (keys value))))
    (if (and ttl-fn (ttl-fn value)) nil value)
    (when (some? value) (prep-attribute-value nil -1 value))))

(defn lookup-attribute-value
  "Lookup the attribute value in the node, optionally trying the attribute for a nested path.
   Returned values are guaranteed to be properly initialized and relevant, or otherwise nil."

  [node attribute & [ttl-fn]]

  (let [value (get node attribute
               (get-in node (attribute->path attribute)))]
    (handle-attribute-value value ttl-fn)))

(def uninit-key :___!uninitialized!___)

(defn uninitialized-value [node attribute]
  {:___!uninitialized!___ {:node      node
                           :attribute (->attribute-id attribute)}})

(defn uninitialized? [x]
  (and (map? x) (contains? x :___!uninitialized!___)))

(defn endpoint-query?
  "Check whether the conditions are right to issue an endpoint query"

  [{:keys [blueprints] :as state}
   {:keys [attribute sources-data] :as args}]

  (let [{:keys [type sources eval-on-condition]} (get-attribute blueprints attribute)]

    ;; This has to be an endpoint attribute
    ;; AND either 'this' is its only source, or its sources' data isn't empty
    ;; AND all existing sources' data are properly initialized
    (and (= :endpoint type)
         (or (= [[:this]] sources)
             (not-empty sources-data))
         (not (some uninitialized? sources-data))
         (eval-on-condition sources-data))))

(defn query-attribute
  "When the conditions are right for an endpoint query, invokes the Connector/query api with
   the endpoint connector designated by the 'connector-id'"

  [{:keys [endpoint-connectors] :as state}
   {:keys [attribute] :as args}]

  (when (endpoint-query? state args)
    (let [{:keys [connector-id]} attribute
          [c] (filter (comp #{connector-id} endpoint-connector/get-id) endpoint-connectors)]
      (if (some? c)
        (endpoint-connector/query c args)))))

(defn as-ref-ids [{:keys [type]} data]
  (case type
   (:refs :pseudo-refs) (map (comp :id val) data)
   :ref (:id data)
   data))

(defn prep-external-binding [sources env-context external-binding]
  (->> sources
       (filter (comp #{:env} first))
       (map (juxt identity (comp (partial get-in env-context) rest)))
       (into external-binding)))

(defn handle-return-value
  "If the attribute's value is initialized, return its internal :data field,
   Otherwise if the attribute is dynamic in essence, return the 'uninitialized' marker;
   Else returns nil."

  ([blueprints node attribute convert-fn {data :data :as value}]
   (let [attribute (get-attribute blueprints attribute)]
     (if (some? value)
      (convert-fn attribute data)
      (when (dynamic? (get-attribute blueprints attribute))
        (uninitialized-value node attribute)))))
  ([blueprints node attribute value]
   (handle-return-value blueprints node attribute (fn [_ data] data) value)))

(defn get-endpoint-data
  "If 'this' was previously retrieved and is still valid, return it;
   Else query fresh data"

  [state args sources-data this]

  (if this
    [this]
    (s->> sources-data
              (assoc args :sources-data)
              (query-attribute state)
              (vector))))