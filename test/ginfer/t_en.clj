(ns ginfer.t-en
  (:require [midje.sweet :refer :all]
            [taoensso.timbre :as logger]
            [ginfer.attributes.en :refer [export-blueprints import-blueprints]]
            [ginfer.attributes.core :refer :all]
            [ginfer.steps.core :refer :all]
            [ginfer.core :refer :all]))

(background
  (around :facts
          (let []
            (logger/with-level :info ?form))))

(def blueprints-en
  '("dog has owner and person has pet"
     "dog has-flees?"
     "person has-flees? boolean from [[pet has-flees?]]"))

(defn run-scenario [events]
  (fact "Dog's owners always get flees"
        (let [blueprints (export-blueprints
                           (import-blueprints blueprints-en))]
          (-> blueprints
              (infer events :dialect "en")
              (get-data "person/joe" :person/has-flees?))) => truthy))

(midje.config/at-print-level
  :print-facts

  (fact "symmetry of blueprints and natural language"
    (export-blueprints
      (import-blueprints
        blueprints-en)) => '("dog has owner and person has pet"
                   "dog has-flees?"
                   "person has pet and dog has owner"
                   "person has-flees? boolean from [[pet has-flees?]]"))

  (run-scenario ["joe's pet is snoopy"
                 "snoopy dog/has-flees? is true"])

  (run-scenario ["snoopy dog/has-flees? is true"
                 "joe's pet is snoopy"])

  (run-scenario ["snoopy's owner is joe"
                 "snoopy dog/has-flees? is true"])

  (run-scenario ["snoopy dog/has-flees? is true"
                 "snoopy's owner is joe"])

  (fact "foo"
    (let [blueprints ["company has-many departments and department has company"
                      "department has-many teams and team has department"
                      "team has-many employees and employee has team"
                      "employee allegiance identity from [[team department id]]"]
          events ["foo departments add goo"
                  "goo teams add engineers"
                  "engineers employees add joe"]
          more-events ["foo departments add zoo"
                       "engineers department is zoo"]
          state1 (infer blueprints events :dialect "en")
          state2 (infer blueprints more-events
                        :connectors (get-in state1 [:persistence :connectors])
                        :dialect "en")]
      (get-data state1 "employee/joe" :employee/allegiance) => "department/goo"
      (get-data state2 "employee/joe" :employee/allegiance) => "department/zoo"))
  )