(ns ginfer.t-vicious-cycle
  (:require [midje.sweet :refer :all]
            [taoensso.timbre :as logger]
            [ginfer.core :refer :all]))

;demonstrates a case of cyclic inference

(background
  (around :facts
          (let []
            (logger/with-level :info ?form))))

(defn inverse [n]
  (/ 1 n))

(def blueprints
  '("poverty-rate inverse from [[socioeconomic-status]]"
     "socioeconomic-status inverse from [[poverty-rate]]"))

(def stats "stats/population")

(midje.config/at-print-level
  :print-facts

  (fact "high poverty rates => low socioeconomic status"
        (-> blueprints
            (infer ["stats/population poverty-rate is 100.0"] :dialect "en")
            (get-data stats :socioeconomic-status)) => 0.01)

  (fact "low socioeconomic status => high poverty rates"
        (-> blueprints
            (infer ["stats/population socioeconomic-status is 0.01"] :dialect "en")
            (get-data stats :poverty-rate)) => 100.0))
