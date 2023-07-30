(ns ginfer.t-riddle
  (:require [midje.sweet :refer :all]
            [taoensso.timbre :as logger]
            [ginfer.attributes.core :refer :all]
            [ginfer.steps.core :refer :all]
            [persistroids.connector :refer [Connector]]
            [ginfer.core :refer :all]))

;demonstrates.. well, everything
;this is a self-bootstrapping/self-solving riddle
;it creates an elaborate graph model, and runs events all over

(background
  (around :facts
          (let []
            (logger/with-level :info ?form))))

;; RIDDLE:
;how many ways can a person traverse up a stairway,
;if allowed to skip one stair with each step,
;for any arbitrary length of stairway?

(def stairs-db (atom {}))
(def stairs-connector
  (reify Connector
    (get-id [this] "stairs")
    (read [this [id attribute]]
      (get @stairs-db id))
    (write [this [id attribute] value]
      (swap! stairs-db assoc id value)
      nil)))

(defn infer-idx [idx]
  (when (some? idx)
     (dec idx)))

(defn infer-one-below [idx]
  (when (and (some? idx) (pos? idx))
     (str "stair/" (dec idx))))

(defn calc-next [n-1 n-2]
  ((fnil + 0 1) n-1 n-2))

(def blueprints
  '("stair has one-below and stair has one-above"
     "stair has two-below and stair has two-above"
     "stair index infer_idx from [[one-above index]]"
     "stair one-below infer_one_below from [[index]]"
     "stair paths-up-to-here calc_next from [[two-below paths-up-to-here] [one-below paths-up-to-here]]"
     "stair two-below identity from [[one-below one-below id]]"))

;a single event will bootstrap and solve the puzzle for 7 arbitrary stairs
(def events
  ["stair/7 index is 7"])

(midje.config/at-print-level
  :print-facts

  (fact "riddle solution"

        (infer blueprints events
               :connectors [stairs-connector]
               :dialect "en")

        (->> @stairs-db
             (sort-by key)
             (map (comp #(get-in % [:paths-up-to-here :data]) val)))
        =>


        ;the correct answer, given via the fibonacci sequence:
        [1 1 2 3 5 8 13 21]))