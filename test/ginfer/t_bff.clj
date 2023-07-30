(ns ginfer.t-bff
  (:require [midje.sweet :refer :all]
            [taoensso.timbre :as logger]
            [ginfer.attributes.core :refer :all]
            [ginfer.steps.core :refer :all]
            [ginfer.core :refer :all]))

;demonstrates referencing,
;and how events travel along a path of connected nodes

(background
  (around :facts
          (let []
            (logger/with-level :info ?form))))

(def blueprints
  {:friends-with           (->ref :friend-of)
   :friend-of              (->ref :friends-with)
   :under-phishing-attack? (->inferred identity
                                       [[:friends-with :under-phishing-attack?]])})
(def en-blueprints
  ["A person can be friends-with, and a person can be a friend-of"
   "A person under-phishing-attack? identity from [[friends-with under-phishing-attack?]]"])

(def events
  (-> []
      (->link "person/me" :friends-with "person/alice" :friend-of)
      (->link "person/alice" :friends-with "person/bob" :friend-of)
      (->update "person/bob" :under-phishing-attack? true)))
(def en-events
  ["me friends-with alice"
   "alice friends-with bob"
   "bob under-phishing-attack? is true"])

(midje.config/at-print-level
  :print-facts

  ;bob is compromised, therefore I'm compromised
  (fact
    "the friend of my friend is.. a liability?"

    (-> blueprints
        (infer events)
        (get-data "person/me" :under-phishing-attack?)) => truthy

    ;natural language
    (-> en-blueprints
        (infer en-events :dialect "en")
        (get-data "person/me" :under-phishing-attack?)) => truthy))