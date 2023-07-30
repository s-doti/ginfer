(ns ginfer.t-pets-life
  (:require [midje.sweet :refer :all]
            [taoensso.timbre :as logger]
            [ginfer.attributes.core :refer :all]
            [ginfer.steps.core :refer :all]
            [ginfer.core :refer :all]))

;demonstrates order-agnostic events leading to consistent outcome

(background
  (around :facts
          (let []
            (logger/with-level :info ?form))))

(def blueprints
  {:person/pet        (->ref :dog/owner)
   :dog/owner         (->ref :person/pet)
   :dog/has-flees?    (generic)
   :person/has-flees? (->inferred boolean
                                  [[:person/pet :dog/has-flees?]])})

(def joe->snoopy (->link [] "person/joe" :person/pet "dog/snoopy" :dog/owner))
(def snoopy->joe (->link [] "dog/snoopy" :dog/owner "person/joe" :person/pet))
(def snoopy-has-flees (->update [] "dog/snoopy" :dog/has-flees? true))

;poor joe gets flees in any outcome
(defn run-scenario [events]
  (fact "Dog's owners always get flees"
        (-> blueprints
            (infer events)
            (get-data "person/joe" :person/has-flees?)) => truthy))

(midje.config/at-print-level
  :print-facts

  (run-scenario (concat joe->snoopy snoopy-has-flees))
  (run-scenario (concat snoopy->joe snoopy-has-flees))
  (run-scenario (concat snoopy-has-flees joe->snoopy))
  (run-scenario (concat snoopy-has-flees snoopy->joe)))