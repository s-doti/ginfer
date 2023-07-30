(ns ginfer.utils
  (:import (java.time Duration)))

(def ->node-type (comp first #(clojure.string/split % #"/" 2)))
(def ->node-id (comp second #(clojure.string/split % #"/" 2)))

(defn init-node [node] {})

(defn map-vals [f m]
  (->> m
       (map (fn [[k v]] [k (f v)]))
       (into {})))

(defn filter-vals [f m]
  (->> m
       (filter (comp f second))
       (into {})))

(defn collify [arg]
  (cond
    (map? arg) [arg]
    (coll? arg) arg
    (some? arg) [arg]))

(defn get-time-ms [] (System/currentTimeMillis))

;eg. 3d or 10m or 1s..
(defn duration-in-millis [duration-str]
  (let [normalized-duration-str (clojure.string/upper-case duration-str)]
    (cond->> normalized-duration-str
             (not (clojure.string/includes? normalized-duration-str "D")) (str \T)
             :prefix-P (str \P)
             :parse (Duration/parse)
             :in-ms (.toMillis))))

(defmacro with-altered-var-root [v val body]
  `(let [original-val# (var-get ~v)]
     (try
       (alter-var-root ~v (constantly ~val))
       ~body
       (finally
         (alter-var-root ~v (constantly original-val#))))))

(defn ifnil [x f]
  (if (nil? x) (f) x))
