(ns ginfer.t-debugger
  (:require [midje.sweet :refer :all]
            [taoensso.timbre :as logger]
            [clojure.core.async :refer [go timeout <!]]
            [ginfer.attributes.core :refer :all]
            [ginfer.steps.core :refer :all]
            [ginfer.core :refer :all]))

(background
  (around :facts
          (let []
            (logger/with-level :info ?form))))

;a debugger gist

(midje.config/at-print-level
  :print-facts

  (fact
    "debugger"

    (let [blueprints {:data-point          (generic)
                      :inferred-data-point (->inferred inc [[:data-point]])}
          events (->update [] "some/node" :data-point 2)

          io (atom {:breakpoint {:type :update-flow}        ;break on :update-flow (specific args also supported)
                    :resume     (promise)})                 ;expect :step-into/over/out or else play to end
          debug-info-entries '(:step :side-effect-outcome :child-steps :visible-branches)
          get-debug-info-collected #(keys (dissoc @io :breakpoint :resume))]

      (go
        ;execution will reach the breakpoint.. eventually..
        (while (= 2 (count @io))
          (<! (timeout 1)))
        ;verify debug info collected
        (fact "debug info"
              (get-debug-info-collected) => debug-info-entries)
        ;release debugger to commence execution
        ;could use :step-into/over/out here, but we want to run to end
        (swap! io update :resume deliver :run-to-end))

      ;the following would break per declared breakpoint inside io
      (get-data (infer blueprints events :io io)
                "some/node" :inferred-data-point) => 3

      ;verify debug info collected, ie execution actually stopped at the breakpoint
      (get-debug-info-collected) => debug-info-entries))

  )
