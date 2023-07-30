(ns ginfer.t-core
  (:require [midje.sweet :refer :all]
            [taoensso.timbre :as logger]
            [ginfer.attributes.core :refer :all]
            [ginfer.steps.core :refer :all]
            [ginfer.core :refer :all]))

(background
  (around :facts
          (let []
            (logger/with-level :info ?form))))

;demonstrates the basic core mechanics

(defn squared [edge-len] (* edge-len edge-len))
(defn safe-* [a b] ((fnil * 0 0) a b))
(defn safe-inc [i] ((fnil inc 0) i))

(midje.config/at-print-level
  :print-facts

  (fact
    "empty declaration does absolutely nothing"
    (let [blueprints {}
          events []
          final-state (infer blueprints events)]
      (not-empty (:nodes final-state)) => nil))

  (fact
    "data point attribute declaration"

    ;node ids are name-spaced strings as a convention
    ;attribute ids are keywords, optionally name-spaced as well, or otherwise global
    ;in-storage, attributes are stripped of their namespaces
    (let [blueprints {:some/data-point (generic)}
          events (->update [] "some/node" :some/data-point "some arbitrary value")
          final-state (infer blueprints events)]

      (get-data final-state "some/node" :some/data-point) => "some arbitrary value"))

  ;inference

  (fact
    "data point attribute inference declaration"

    (let [blueprints {:data-point          (generic)
                      :inferred-data-point (->inferred inc [[:data-point]])}
          events (->update [] "some/node" :data-point 2)
          final-state (infer blueprints events)]

      (get-data final-state "some/node" :inferred-data-point) => 3))

  (fact
    "data point attribute inference with env binding"

    (let [blueprints {:circle/radius (generic)
                      :circle/area   (->inferred (fn [pi r] (* 2 pi r))
                                                 [[:env :constants :pi]
                                                  [:circle/radius]])}
          events (->update [] "circle/r2" :circle/radius 2)
          env {:constants {:pi 3.14}}
          final-state (infer blueprints events :env-context env)]

      (get-data final-state "circle/r2" :circle/area) => 12.56)

    ;natural language
    (let [blueprints-en ["circle radius"
                         "circle area * from [[radius] [env constants factor]]"]
          events-en ["r2's radius is 2"]
          env {:constants {:factor 6.28}}]
      (-> blueprints-en
          (infer events-en :dialect "en" :env-context env)
          (get-data "circle/r2" :circle/area)) => 12.56))

  (fact
    "data point notifies multiple inferred data points"

    (let [blueprints {:square/edge-len  (generic)
                      :square/area      (->inferred (fn [edge-len] (* edge-len edge-len))
                                                    [[:square/edge-len]])
                      :square/perimeter (->inferred (partial * 4)
                                                    [[:square/edge-len]])}
          events (->update [] "square/3x3" :square/edge-len 3)
          final-state (infer blueprints events)]

      (get-data final-state "square/3x3" :square/area) => 9
      (get-data final-state "square/3x3" :square/perimeter) => 12)

    ;natural language
    (let [blueprints ["square edge-len"
                      "square area squared from [[edge-len]]"
                      "square perimeter * from [[edge-len] [env constants factor]]"]
          events ["3x3's edge-len is 3"]
          env {:constants {:factor 4}}
          state (infer blueprints events :dialect "en" :env-context env)]
      (get-data state "square/3x3" :square/area) => 9
      (get-data state "square/3x3" :square/perimeter) => 12))

  (fact
    "data point inferred from multiple data points"

    (let [blueprints {:rectangle/w    (generic)
                      :rectangle/h    (generic)
                      :rectangle/area (->inferred (fnil * 0 0)
                                                  [[:rectangle/w]
                                                   [:rectangle/h]])}
          events (-> []
                     (->update "rectangle/3x2" :rectangle/w 3)
                     (->update "rectangle/3x2" :rectangle/h 2))
          final-state (infer blueprints events)]

      (get-data final-state "rectangle/3x2" :rectangle/area) => 6)

    ;natural language
    (let [final-state (infer ["rectangle w"
                              "rectangle h"
                              "rectangle area safe-* from [[w] [h]]"]
                             ["3x2's w is 3"
                              "3x2's h is 2"]
                             :dialect "en")]

      (get-data final-state "rectangle/3x2" :rectangle/area) => 6))

  ;reference

  (fact
    "data points referencing one another"

    (let [blueprints {:connects-to    (->ref :connected-from)
                      :connected-from (->ref :connects-to)}
          events (->link [] "some/node" :connects-to "another/node" :connected-from)
          final-state (infer blueprints events)]

      (get-refids final-state "another/node" :connected-from) => "some/node")

    ;natural language
    (let [blueprints ["any can connects-to and any can be connected-from"]
          events ["some/node connects-to another/node"]
          final-state (infer blueprints events :dialect "en")]

      (get-refids final-state "another/node" :connected-from) => "some/node"))

  ;inference via reference

  (fact
    "data point is inferred via reference path"

    (let [blueprints {:connects-to         (->ref :connected-from)
                      :connected-from      (->ref :connects-to)
                      :data-point          (generic)
                      :inferred-data-point (->inferred (fnil inc 0) [[:connects-to :data-point]])}
          events (-> []
                     (->link "some/node" :connects-to "another/node" :connected-from)
                     (->update "another/node" :data-point 2))
          final-state (infer blueprints events)]

      (get-data final-state "some/node" :inferred-data-point) => 3)

    ;natural language
    (let [blueprints ["any can connects-to and any can be connected-from"
                      "data-point"
                      "inferred-data-point safe-inc from [[connects-to data-point]]"]
          events ["some/node connects-to another/node"
                  "another/node's data-point is 2"]
          final-state (infer blueprints events :dialect "en")]

      (get-data final-state "some/node" :inferred-data-point) => 3))

  (fact
    "multi-refs"

    (let [blueprints {:children (->refs :parent)
                      :parent   (->ref :children)}
          events (-> []
                     (->link "person/bob" :children "person/timmy" :parent)
                     (->link "person/bob" :children "person/sarah" :parent)
                     (->link "person/bob" :children "person/jesse" :parent)
                     (->link "person/timmy" :parent "person/joe" :children))
          final-state (infer blueprints events)]

      (get-refids final-state "person/timmy" :parent) => "person/joe"
      (get-refids final-state "person/bob" :children) => ["person/sarah" "person/jesse"])

    ;natural language
    (let [blueprints ["person has-many children and person has parent"]
          events ["bob's children include timmy"
                  "bob's children include sarah"
                  "bob's children include jesse"
                  "timmy's parent is joe"]
          final-state (infer blueprints events :dialect "en")]

      (get-refids final-state "person/timmy" :person/parent) => "person/joe"
      (get-refids final-state "person/bob" :person/children) => ["person/sarah" "person/jesse"]))

  (fact
    "multi-refs downsized"
    (let [blueprints {:planet/moon-names (generic)
                      :planet/moons      (-> (->refs :moon/planet)
                                             (->make-dynamic identity
                                                             [[:planet/moon-names]]))
                      :moon/planet       (->ref :planet/moons)}
          events (-> []
                     (->update "planet/jupiter" :planet/moon-names
                               ["moon/io" "moon/europa" "moon/ganymede" "moon/callisto"])
                     (->update "planet/jupiter" :planet/moon-names ["moon/io" "moon/ganymede"]))
          final-state (infer blueprints events)]
      (get-refids final-state "planet/jupiter" :planet/moons) => ["moon/io" "moon/ganymede"])

    ;natural language
    (let [blueprints ["planet moon-names"
                      "planet has-many moons and moon has planet"
                      "planet moons identity from [[moon-names]]"]
          events ["jupiter's moon-names are [\"moon/io\",\"moon/europa\",\"moon/ganymede\",\"moon/callisto\"]"
                  "jupiter's moon-names are [\"moon/io\",\"moon/ganymede\"]"]
          final-state (infer blueprints events :dialect "en")]

      (get-refids final-state "planet/jupiter" :planet/moons) => ["moon/io" "moon/ganymede"]))

  (fact
    "error handling"

    (let [blueprints {:data-point          (generic)
                      :inferred-data-point (->inferred inc [[:data-point]])}
          events (->eval [] "some/node" :inferred-data-point)]

      ;leads to a null pointer exception with a clear stack trace
      ;as the inc fn is run with a nil value
      (infer blueprints events) => (throws Exception
                                           #"SEPL failure occurred in step :eval-flow"
                                           #":node \"some/node\", :attribute :inferred-data-point")))

  (fact
    "optimizer - not presently implemented"

    (let [blueprints {:data-point          (generic)
                      :inferred-data-point (->inferred inc [[:data-point]])}
          events (->update [] "some/node" :data-point 2)]

      (with-redefs [ginfer.side-effects.optimizer/side-effects-optimizer
                    (fn [state steps]
                      (fact "optimizer batches steps"
                            (distinct (map :node steps)) => ["some/node"])
                      state)]
        (get-data (infer blueprints events) "some/node" :inferred-data-point)) => 3))

  )