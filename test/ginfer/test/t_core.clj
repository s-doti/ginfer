(ns ginfer.test.t-core
  (:require [midje.sweet :refer :all]
            [ginfer.attributes.core :refer :all]
            [ginfer.test.core :refer :all]))

(defn generate-mock-input-data [blueprints]
  (->> (get-in blueprints [:attr-under-test :sources])
       (get-in blueprints [:foo/attr-under-test :sources])
       (count)
       (range)
       (map (partial str "input-data-"))))

(tabular

  (fact
    (let [inputs (generate-mock-input-data ?blueprints)
          mocking-events (generate-mocking-events ?blueprints :attr-under-test inputs)]
      (map (juxt :type (comp vals :args)) mocking-events))
    => ?outcome)

  ?blueprints ?outcome

  {} []

  {:attr-under-test (generic)} []

  {:attr-under-test (->ref :another-attr)
   :another-attr    (->ref :attr-under-test)} []

  {:attr-under-test (->inferred (fn [& args]) [])} []

  {:attr-1          (generic)
   :attr-2          (->ref :symm-attr-2)
   :symm-attr-2     (->ref :attr-2)
   :attr-3          (generic)
   :attr-4          (->ref :symm-attr-4)
   :symm-attr-4     (->ref :attr-4)
   :attr-5          (->ref :symm-attr-5)
   :symm-attr-5     (->ref :attr-5)
   :attr-6          (generic)
   :attr-7          (->ref :symm-attr-7)
   :symm-attr-7     (->ref :attr-7)
   :attr-8          (->ref :symm-attr-8)
   :symm-attr-8     (->ref :attr-8)
   :attr-9          (->ref :symm-attr-9)
   :symm-attr-9     (->ref :attr-9)
   :attr-10         (generic)
   :attr-under-test (->inferred (fn [& args])
                                [[:attr-1]
                                 [:attr-2 :attr-3]
                                 [:attr-4 :attr-5 :attr-6]
                                 [:attr-7 :attr-8 :attr-9 :attr-10]])}

  '([:update-flow ("some/output" :attr-1 "input-data-0")]
    [:link-flow ("some/output" :attr-2 "some/input-1" :symm-attr-2 nil true)]
    [:update-flow ("some/input-1" :attr-3 "input-data-1")]
    [:link-flow ("some/output" :attr-4 "some/node-id" :symm-attr-4 nil true)]
    [:link-flow ("some/node-id" :attr-5 "some/input-2" :symm-attr-5 nil true)]
    [:update-flow ("some/input-2" :attr-6 "input-data-2")]
    [:link-flow ("some/output" :attr-7 "some/node-id" :symm-attr-7 nil true)]
    [:link-flow ("some/node-id" :attr-8 "some/node-id" :symm-attr-8 nil true)]
    [:link-flow ("some/node-id" :attr-9 "some/input-3" :symm-attr-9 nil true)]
    [:update-flow ("some/input-3" :attr-10 "input-data-3")]))

(fact
  (let [blueprints {:foo/attr-1          (generic)
                    :foo/attr-2          (->ref :goo/symm-attr-2)
                    :goo/symm-attr-2     (->ref :foo/attr-2)
                    :goo/attr-3          (generic)
                    :foo/attr-4          (->ref :goo/symm-attr-4)
                    :goo/symm-attr-4     (->ref :foo/attr-4)
                    :goo/attr-5          (->ref :doo/symm-attr-5)
                    :doo/symm-attr-5     (->ref :goo/attr-5)
                    :doo/attr-6          (generic)
                    :foo/attr-7          (->ref :goo/symm-attr-7)
                    :goo/symm-attr-7     (->ref :foo/attr-7)
                    :goo/attr-8          (->ref :doo/symm-attr-8)
                    :doo/symm-attr-8     (->ref :goo/attr-8)
                    :doo/attr-9          (->ref :zoo/symm-attr-9)
                    :zoo/symm-attr-9     (->ref :doo/attr-9)
                    :zoo/attr-10         (generic)
                    :foo/attr-under-test (->inferred (fn [& args])
                                                     [[:foo/attr-1]
                                                      [:foo/attr-2 :goo/attr-3]
                                                      [:foo/attr-4 :goo/attr-5 :doo/attr-6]
                                                      [:foo/attr-7 :goo/attr-8 :doo/attr-9 :zoo/attr-10]])}
        inputs (generate-mock-input-data blueprints)
        mocking-events (generate-mocking-events blueprints :foo/attr-under-test inputs)]
    (map (juxt :type (comp vals :args)) mocking-events))
  => '([:update-flow ("foo/output" :foo/attr-1 "input-data-0")]
       [:link-flow ("foo/output" :foo/attr-2 "goo/input-1" :goo/symm-attr-2 nil true)]
       [:update-flow ("goo/input-1" :goo/attr-3 "input-data-1")]
       [:link-flow ("foo/output" :foo/attr-4 "goo/node-id" :goo/symm-attr-4 nil true)]
       [:link-flow ("goo/node-id" :goo/attr-5 "doo/input-2" :doo/symm-attr-5 nil true)]
       [:update-flow ("doo/input-2" :doo/attr-6 "input-data-2")]
       [:link-flow ("foo/output" :foo/attr-7 "goo/node-id" :goo/symm-attr-7 nil true)]
       [:link-flow ("goo/node-id" :goo/attr-8 "doo/node-id" :doo/symm-attr-8 nil true)]
       [:link-flow ("doo/node-id" :doo/attr-9 "zoo/input-3" :zoo/symm-attr-9 nil true)]
       [:update-flow ("zoo/input-3" :zoo/attr-10 "input-data-3")]))

;API tests from here on, these demonstrate proper usage

;basic and simple test to demo basic usage pattern
(let [blueprints {:data-point          (generic)
                  :inferred-data-point (->inferred inc [[:data-point]])}
      use-cases [[[1] 2]                                    ;vector of pairs
                 [[-1] 0]]]                                 ;pairs of inputs-output
  (verify-attribute blueprints
                    :inferred-data-point
                    use-cases))

;elaborate case, generic attributes
(let [blueprints {:attr-1          (generic)
                  :attr-2          (->ref :symm-attr-2)
                  :symm-attr-2     (->ref :attr-2)
                  :attr-3          (generic)
                  :attr-4          (->ref :symm-attr-4)
                  :symm-attr-4     (->ref :attr-4)
                  :attr-5          (->ref :symm-attr-5)
                  :symm-attr-5     (->ref :attr-5)
                  :attr-6          (generic)
                  :attr-7          (->ref :symm-attr-7)
                  :symm-attr-7     (->ref :attr-7)
                  :attr-8          (->ref :symm-attr-8)
                  :symm-attr-8     (->ref :attr-8)
                  :attr-9          (->ref :symm-attr-9)
                  :symm-attr-9     (->ref :attr-9)
                  :attr-10         (generic)
                  :attr-under-test (->inferred (fn [& args]
                                                 ;(prn args)
                                                 (reduce (fnil + 0 0)
                                                         args))
                                               [[:attr-1]
                                                [:attr-2 :attr-3]
                                                [:attr-4 :attr-5 :attr-6]
                                                [:attr-7 :attr-8 :attr-9 :attr-10]])}
      use-cases [[[1 2 3 4] 10]]]
  (verify-attribute blueprints
                    :attr-under-test
                    use-cases))

;elaborate case, name-spaced attributes
(let [blueprints {:foo/attr-1          (generic)
                  :foo/attr-2          (->ref :goo/symm-attr-2)
                  :goo/symm-attr-2     (->ref :foo/attr-2)
                  :goo/attr-3          (generic)
                  :foo/attr-4          (->ref :goo/symm-attr-4)
                  :goo/symm-attr-4     (->ref :foo/attr-4)
                  :goo/attr-5          (->ref :doo/symm-attr-5)
                  :doo/symm-attr-5     (->ref :goo/attr-5)
                  :doo/attr-6          (generic)
                  :foo/attr-7          (->ref :goo/symm-attr-7)
                  :goo/symm-attr-7     (->ref :foo/attr-7)
                  :goo/attr-8          (->ref :doo/symm-attr-8)
                  :doo/symm-attr-8     (->ref :goo/attr-8)
                  :doo/attr-9          (->ref :zoo/symm-attr-9)
                  :zoo/symm-attr-9     (->ref :doo/attr-9)
                  :zoo/attr-10         (generic)
                  :foo/attr-under-test (->inferred (fn [& args]
                                                     ;(prn args)
                                                     (reduce (fnil + 0 0)
                                                             args))
                                                   [[:foo/attr-1]
                                                    [:foo/attr-2 :goo/attr-3]
                                                    [:foo/attr-4 :goo/attr-5 :doo/attr-6]
                                                    [:foo/attr-7 :goo/attr-8 :doo/attr-9 :zoo/attr-10]])}
      use-cases [[[1 2 3 4] 10]]]
  (verify-attribute blueprints
                    :foo/attr-under-test
                    use-cases))

;ensure bootstrapping via the :created event doesn't ruin the party
(let [blueprints {:global-data-point         (-> (->inferred identity [[:id]])
                                                 (->on-event :created)
                                                 (->immutable))
                  :typed/data-point          (->inferred identity [[:global-data-point]])
                  :typed/inferred-data-point (-> (->inferred identity [[:typed/data-point]])
                                                 (->on-event :created))}
      use-cases [[["typed/123"] "typed/123"]]]
  (verify-attribute blueprints
                    :typed/inferred-data-point
                    use-cases))

;ensure endpoints do not ruin the party
(let [blueprints {:typed/endpoint            (-> (->endpoint "id")
                                                 (->on-event :created))
                  :typed/data-point          (->inferred identity [[:typed/endpoint]])
                  :typed/inferred-data-point (->inferred identity [[:typed/data-point]])}
      use-cases [[["typed/123"] "typed/123"]]]
  (verify-attribute blueprints
                    :typed/inferred-data-point
                    use-cases))
