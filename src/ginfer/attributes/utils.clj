(ns ginfer.attributes.utils
  (:require [ginfer.utils :refer :all]))

(defn ->attribute-id
  ""
  [attr]
  (cond->> attr
           (map? attr) :id))

(defn get-attribute
  "Lookup the attribute by given keyword id (or return it as if otherwise)"
  [attributes attribute]
  (cond->> attribute
           (keyword? attribute)
           (get attributes)))

(def strip-ns (comp keyword name))

(defn prep-attribute-value [value version data]
  (let [now-ms (get-time-ms)]
    (cond-> value
            (nil? value) (assoc :version version :created now-ms)
            :touch (assoc :modified now-ms)
            :mutate! (assoc :data data))))

;refs

(defn gen-ref [node-id attribute meta]
  (merge meta (let [now (get-time-ms)]
                {:id                  node-id
                 :symmetric-attribute attribute
                 :created             now
                 :modified            now})))
(defn sanitize-ref [ref] (dissoc ref :created :modified))
(def ->ref-id (juxt :id :symmetric-attribute))
(defn refs-not= [ref1 ref2] (not= (sanitize-ref ref1) (sanitize-ref ref2)))
(defn lookup-ref
  ([ref-data ref-type node attribute] (lookup-ref ref-data ref-type (gen-ref node attribute {})))
  ([ref-data ref-type ref]
   (let [ref (if (= ref-type :ref)
               ref-data
               (get ref-data (->ref-id ref)))]
     (when ref
       (update ref :symmetric-attribute keyword)))))

(defn get-ref [refs ref] (get refs (->ref-id ref)))

(def ref? (comp #{:ref :refs} :type))
(def dynamic? (comp #{:dynamic :endpoint} :type))
(def inferred? dynamic?)
