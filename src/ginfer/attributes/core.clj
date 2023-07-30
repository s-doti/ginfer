(ns ginfer.attributes.core
  (:require [ginfer.utils :refer :all]
            [ginfer.attributes.defaults :refer :all]
            [ginfer.attributes.utils :refer :all]))

(def ->static ->attribute)
(def generic ->attribute)
(def ->static-many (-> (->static) (merge default-coll-attribute-flags)))
(defn ->dynamic [f sources & {:keys [async? derefable?] :as args}]
  (->> {:notifiers sources :sources sources :eval-fn f}
       (merge default-dynamic-attribute-flags args)
       (seq)
       (apply concat)
       (apply ->attribute)))
(def ->inferred ->dynamic)
(defn ->ref [symmetric-attribute & {:keys [meta-fn] :as args}]
  (->> {:symmetric-attribute symmetric-attribute :diff-fn refs-not=}
       (merge default-ref-attribute-flags args)
       (seq)
       (apply concat)
       (apply ->attribute)))
(defn ->refs [symmetric-attribute & {:keys [] :as args}]
  (->> {}
       (merge default-refs-attribute-flags args)
       (seq)
       (apply concat)
       (apply ->ref symmetric-attribute)))
(defn ->endpoint [url-or-id & {:keys [] :as args}]
  (->> (if (or (clojure.string/starts-with? url-or-id "http://")
               (clojure.string/starts-with? url-or-id "https://"))
         {:url url-or-id :connector-id "http"}
         {:connector-id url-or-id})
       (merge default-endpoint-attribute-flags args)
       (seq)
       (apply concat)
       (apply ->attribute)))
(defn ->make-dynamic [attr f sources & {:keys [] :as args}]
  (merge attr
         {:eval-fn f :notifiers sources :sources sources
          :eval-on-condition (constantly true)}
         args))
(defn ->with-meta [ref-attr meta-fn] (merge ref-attr {:meta-fn meta-fn}))
(defn ->immutable [dynamic-attr] (merge dynamic-attr {:diff-fn (fn [curr-val _] (nil? curr-val))}))
(def ->idempotent ->immutable)
(def ->once ->immutable)
(def ->const ->immutable)
(defn ->async [dynamic-attr] (merge dynamic-attr {:async? true}))
(defn ->derefable [dynamic-attr & [timeout-ms timeout-val]]
  (merge dynamic-attr {:derefable? true :timeout-ms timeout-ms :timeout-val timeout-val}))
(defn ->on-change [dynamic-attr notifiers] (update dynamic-attr :notifiers concat notifiers))
(defn ->tts [dynamic-attr period-str] (merge dynamic-attr {:tts period-str}))
(defn ->fire-event [attr event] (update attr :notify-events conj event))
(defn ->on-event [attr event] (-> attr
                                  (update :on-events conj event)
                                  (update :notify-events (partial remove #{event}))))
(defn ->on-discovery [attr] (->on-event attr :discovery))
(defn ->on-visit [attr] (->on-event attr :visit))
(defn ->on-boot [attr] (->immutable (->on-event attr :boot)))
(defn ->boot [attr] (merge (->on-boot attr) {#_#_:notify-boot? true}))
