(ns ginfer.steps.en
  (:require [ginfer.attributes.utils :refer :all]
            [ginfer.steps.core :refer :all]))

(defn lookup [blueprints attribute-name]
  (let [[by-name :as all-matching] (map key (filter #(= attribute-name (name (key %))) blueprints))]
    (if (< 1 (count all-matching))
      (throw (Exception. (str "Multiple matching attributes found " (into [] all-matching))))
      (if by-name
        by-name
        (let [direct-match (keyword attribute-name)]
          (if (contains? blueprints direct-match)
            direct-match
            (do (prn (keys blueprints))
              (throw (Exception. (str "Attribute not found " attribute-name))))))))))

(defn parse-event [blueprints event]
  (let [update-matcher (re-matcher #"(?<srcId>[^ ]*) (?<attribute>[^ ]*) (?:|.* )(?<value>[^ ]*)" event)
        eval-matcher (re-matcher #"(?<srcId>[^ ]*) (?<type>[^ ]*) (?<attribute>[^ ]*) (?<action>[^ ]*)" event)]
    (cond
      (.matches update-matcher)
      (let [attribute-name (.group update-matcher "attribute")
            attribute-id (lookup blueprints attribute-name)
            type (namespace attribute-id)
            {symmetric-attribute :symmetric-attribute :as attribute} (get-attribute blueprints attribute-id)
            ref? (ref? attribute)
            action (if ref? "link" "update")
            src-id (.group update-matcher "srcId")
            value (.group update-matcher "value")
            ]
        (case action
          "link" {:action              "link"
                  :src-id              (if type (str type \/ src-id) src-id)
                  :attribute-id        attribute-id
                  :dst-id              (if-let [ns (namespace symmetric-attribute)] (str ns \/ value) value)
                  :symmetric-attribute symmetric-attribute}
          "update" {:action       "update"
                    :src-id       (if type (str type \/ src-id) src-id)
                    :attribute-id attribute-id
                    :value        (let [v (clojure.edn/read-string value)]
                                    (if (symbol? v) value v))}))
      (.matches eval-matcher)
      (let [action (.group eval-matcher "action")
            type (.group eval-matcher "type")
            attribute (.group eval-matcher "attribute")
            attribute-id (keyword (str type \/ attribute))
            ;{:as attribute} (get-attribute blueprints attribute-id)
            src-id (.group eval-matcher "srcId")
            ]
        (case action
          "eval" {:action           "eval"
                  :src-id           (str type \/ src-id)
                  :attribute-id     attribute-id
                  :external-binding {}})))))

(defn gen-event [blueprints events event]
  ;=> (->link "company/cybla" :company/departments "department/rnd" :department/company)
  (let [{:keys [action src-id attribute-id value dst-id symmetric-attribute]} (parse-event blueprints event)]
    (case action
      "update" (->update events src-id attribute-id value)
      "link" (->link events src-id attribute-id dst-id symmetric-attribute)
      "eval" (->eval events src-id attribute-id))))

(defn analyze-events [blueprints events]
  (->> events
       (map clojure.string/trim)
       (remove clojure.string/blank?)
       (map #(clojure.string/replace % #"'s" ""))
       (map #(clojure.string/replace % #"'" ""))
       ;(map #(clojure.string/replace % #" is " " "))
       ;(map #(clojure.string/replace % #" are " " "))
       ;(map #(clojure.string/replace % #" include " " "))
       ;(map #(clojure.string/replace % #" add " " "))
       (reduce (partial gen-event blueprints) [])))