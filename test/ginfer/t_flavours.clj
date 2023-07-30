(ns ginfer.t-flavours
  (:require [midje.sweet :refer :all]
            [taoensso.timbre :as logger]
            [clojure.core.async :refer [<!!]]
            [seamless-async.core :refer [as-seq]]
            [ginfer.core :refer :all]))

(background
  (around :facts
          (let []
            (logger/with-level :info ?form))))

;a demonstration of how infer may be run async, or as lazy steps, or as async lazy steps

(defn sum [counts]
  (apply + counts))

(defn run-scenario [infer-fn]
  (let [blueprints ["company has-many departments and department has company"
                    "company head-count sum from [[departments head-count]]"
                    "department has-many teams and team has department"
                    "department head-count sum from [[teams head-count]]"
                    "team has-many employees and employee has team"
                    "team head-count count from [[employees]]"
                    "employee allegiance identity from [[team department id]]"]
        events ["foo's departments include rnd"
                "rnd's teams include engineers"
                "engineers' employees include joe"]
        more-events ["zoo's company is foo"
                     "engineers' department is zoo"]
        state (infer-fn blueprints events :dialect "en")
        _ (finalize state)
        allegiance1 (get-data state "employee/joe" :employee/allegiance)
        connectors (get-in state [:persistence :connectors])
        state (infer-fn blueprints more-events :connectors connectors :dialect "en")
        _ (finalize state)
        allegiance2 (get-data state "employee/joe" :employee/allegiance)
        ]

    (fact "allegiance before" allegiance1 => "department/rnd")
    (fact "allegiance after" allegiance2 => "department/zoo")
    (fact "head-count" (get-data state "company/foo" :company/head-count) => 1)))

(midje.config/at-print-level
  :print-facts

  (fact "simple infer" (run-scenario infer))
  (fact "async infer" (run-scenario (comp <!! async-infer)))
  (fact "lazy infer" (run-scenario (comp :state last lazy-infer)))
  (fact "lazy async infer" (run-scenario (comp :state last as-seq lazy-async-infer)))
  )