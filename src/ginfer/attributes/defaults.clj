(ns ginfer.attributes.defaults
  (:require [ginfer.attributes.utils :refer [->ref-id]]
            [ginfer.utils :refer :all]))

(def default-diff-fn not=)
(defn default-set-fn [old-value new-value] new-value)
(def default-unset-fn (constantly nil))
(def default-eval-fn identity)
(def default-meta-fn (constantly {}))
(def default-version 1)

(def one-week-ms (* 1000 3600 24 7))
(def one-day-ms (* 1000 3600 24))
(def one-hour-ms (* 1000 3600))
(def one-minute-ms (* 1000 60))

(defn default-ttl-fn [{modified :modified :as v}]
  (< one-week-ms (- (get-time-ms) modified)))
(defn endpoint-ttl-fn [{{error? :error} :data modified :modified :as v}]
  (or (default-ttl-fn v)
      (and error? (< one-hour-ms (- (get-time-ms) modified)))))

(def default-attribute-flags
  {:version             default-version
   :type                :static
   :value-type          :anything
   ;:notify-boot? true
   :notify-events       [:modified]
   :diff-fn             default-diff-fn
   :set-fn              default-set-fn
   :unset-fn            default-unset-fn
   :ttl-fn              default-ttl-fn
   :notify-on-condition (constantly true)})
(def default-dynamic-attribute-flags
  {:type              :dynamic
   :async?            false
   :derefable?        false
   :eval-fn           default-eval-fn
   :eval-on-condition (constantly true)})
(def default-ref-attribute-flags
  {:type       :ref
   :value-type :node
   :meta-fn    default-meta-fn})
(def default-refs-attribute-flags
  {:type     :refs
   :set-fn   (fn set-refs-fn [refs ref] (assoc refs (->ref-id ref) ref))
   :unset-fn dissoc})
(def default-coll-attribute-flags
  {:diff-fn  (comp not contains?)
   :set-fn   (fnil conj #{})
   :unset-fn disj})
(def default-endpoint-attribute-flags
  {:type              :endpoint
   :ttl-fn            endpoint-ttl-fn
   :format-fn         format
   :notifiers         [[:this]]
   :sources           [[:this]]
   :eval-fn           identity
   :diff-fn           (constantly true)
   :eval-on-condition (constantly true)})

(defn get-version [] default-version)

(defn ->attribute
  [& {:keys [version type value-type diff-fn set-fn unset-fn listeners] :as args}]
  (merge default-attribute-flags args))