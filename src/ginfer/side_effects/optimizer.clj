(ns ginfer.side-effects.optimizer)

(defn side-effects-optimizer
  "The optimizer is risky, messy business.
   It is a chance to look forward into next few steps in the execution,
   and do something about them ahead of time.
   A possible optimization may be to carry out async side effects ahead of time,
   so their outcome is ready earlier.
   Yet another optimization is the batching of multiple side effects together, e.g.
   querying once rather than many times.
   The optimizer is expected to return a modified state, so whichever tactic used here,
   it must work through mutating the state."
  [state future-steps] state)