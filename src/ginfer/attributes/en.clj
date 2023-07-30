(ns ginfer.attributes.en
  (:require [taoensso.timbre :as logger]
            [ginfer.attributes.core :refer :all]))

#_(defn extract [extract-instruction [[endpoint]]]
    (->dynamic (from-response (collify extract-instruction)) [[endpoint]]
               :extract-instruction (collify extract-instruction)))

(defn resolve-fn [f]
  (if (keyword? f)
    (name f)
    (-> (re-find #"(?<=\$)([^@]+)(?=@)" (str f))
        (first)
        (clojure.string/replace "__GT_" "->")
        (clojure.string/replace "_QMARK_" "?")
        (clojure.string/replace #"__[\d]*$" "")
        (clojure.string/split #"\$" 2)
        (first))))

(defn export-blueprints [blueprints]
  (sort
    (distinct
      (filter some?
              (for [[id {:keys [type value-type eval-fn sources symmetric-attribute on-events
                                url body-template opts headers
                                extract-instruction
                                eval-on-condition notify-on-condition
                                dsl] :as blueprint}] blueprints]
                ;(prn (str (namespace id) \. (name id) " "))
                (let [entity-type (or (namespace id) "any")]
                  (cond
                    (or (and (or (= :ref type)
                                 (= :refs type))
                             (some? sources))
                        (= :dynamic type)) (str (if entity-type entity-type "")
                                                (if entity-type " " "")
                                                (name id)
                                                (if extract-instruction " extract " " ")
                                                (if extract-instruction (if (sequential? extract-instruction) (mapv (comp symbol name) extract-instruction) extract-instruction) (resolve-fn eval-fn))
                                                " from "
                                                (mapv (partial mapv (comp symbol name)) sources)
                                                (if (not-empty on-events) " on-events " "")
                                                (if (not-empty on-events) (mapv (comp symbol name) on-events) "")
                                                (if (not= "constantly" (resolve-fn eval-on-condition)) " eval-when " "")
                                                (if (not= "constantly" (resolve-fn eval-on-condition)) (resolve-fn eval-on-condition) "")
                                                (if (not= "constantly" (resolve-fn notify-on-condition)) " notify-when " "")
                                                (if (not= "constantly" (resolve-fn notify-on-condition)) (resolve-fn notify-on-condition) "")
                                                )
                    (= :endpoint type) (str (if entity-type entity-type "general")
                                            " "
                                            (clojure.string/replace (name id) "-endpoint" "")
                                            " endpoint "
                                            url
                                            (if body-template (str " body:" (clojure.string/replace body-template " " "")) "")
                                            ;(if (not= default-endpoint-opts opts) (str " opts:" (clojure.string/replace (cheshire/generate-string opts) #" " "")) "")
                                            ;(if (not= default-endpoint-headers headers) (str " headers:" (clojure.string/replace (cheshire/generate-string headers) #" " "&nbsp;")) "")
                                            (if (not-empty on-events) " on-events " "")
                                            (if (not-empty on-events) (mapv (comp symbol name) on-events) ""))
                    (= :static type) (str (if entity-type entity-type "")
                                          (if entity-type " " "")
                                          (name id))
                    (= :ref type) (let [{type2 :type sources2 :sources} (get blueprints symmetric-attribute)
                                        entity-type2 (or (namespace symmetric-attribute) "any")]
                                    (if sources2
                                      (str (if entity-type2 entity-type2 "")
                                           (if (= :ref type2) " has " " has-many ")
                                           (name symmetric-attribute)
                                           " and "
                                           (if entity-type entity-type "")
                                           " has "
                                           (name id))
                                      (str (if entity-type entity-type "")
                                           " has "
                                           (name id)
                                           " and "
                                           (if entity-type2 entity-type2 "")
                                           (if (= :ref type2) " has " " has-many ")
                                           (name symmetric-attribute))))
                    (= :refs type) (let [{type2 :type sources2 :sources} (get blueprints symmetric-attribute)
                                         entity-type2 (namespace symmetric-attribute)]
                                     (if sources2
                                       (str (if entity-type2 entity-type2 "")
                                            (if (= :ref type2) " has " " has-many ")
                                            (name symmetric-attribute)
                                            " and "
                                            (if entity-type entity-type "")
                                            " has-many "
                                            (name id))
                                       (str (if entity-type entity-type "")
                                            " has-many "
                                            (name id)
                                            " and "
                                            (if entity-type2 entity-type2 "")
                                            (if (= :ref type2) " has " " has-many ")
                                            (name symmetric-attribute)))))))))))

(def static-generic-pattern #"^([^ ]*)$")
(defn load-static-generics [blueprints serialized-blueprints]
  (reduce (fn gen-blueprint [blueprints blueprint]
            (if-let [[static-generic? att] (re-matches static-generic-pattern blueprint)]
              (let [fully-qualified-attr (keyword att)]
                (-> blueprints
                    (assoc fully-qualified-attr
                           (ginfer.attributes.core/->static))
                    (update :unhandled disj blueprint)))
              blueprints))
          blueprints
          serialized-blueprints))

(def static-pattern #"^([^ ]*) ([^ ]*)$")
(defn load-statics [blueprints serialized-blueprints]
  (reduce (fn gen-blueprint [blueprints blueprint]
            (if-let [[static-generic? type att] (re-matches static-pattern blueprint)]
              (let [fully-qualified-attr (keyword (str type \/ att))]
                (-> blueprints
                    (assoc fully-qualified-attr
                           (ginfer.attributes.core/->static))
                    (update :unhandled disj blueprint)))
              blueprints))
          blueprints
          serialized-blueprints))

(def relation-pattern #"^([^ ]*) (has|has-many) ([^ ]*) and ([^ ]*) (has|has-many) ([^ ]*)$")
(def attr-fn {"has"      ginfer.attributes.core/->ref
              "has-many" ginfer.attributes.core/->refs})
(defn load-relations [blueprints serialized-blueprints]
  (reduce (fn gen-blueprint [blueprints blueprint]
            (if-let [[relation? type1 rel-type1 att1 type2 rel-type2 att2] (re-matches relation-pattern blueprint)]
              (let [fully-qualified-attr1 (keyword (if (= type1 "any") att1 (str type1 \/ att1)))
                    fully-qualified-attr2 (keyword (if (= type2 "any") att2 (str type2 \/ att2)))]
                (-> blueprints
                    (assoc fully-qualified-attr1
                           ((get attr-fn rel-type1) fully-qualified-attr2))
                    (assoc fully-qualified-attr2
                           ((get attr-fn rel-type2) fully-qualified-attr1))
                    (update :unhandled disj blueprint)))
              blueprints))
          blueprints
          serialized-blueprints))

(def endpoint-pattern #"^([^ ]*) ([^ ]*) endpoint ([^ ]*) ([^:]*):([^ ]*) ([^:]*):([^ ]*) ([^:]*):([^ ]*)(| on-events ([^ ]*))$")
(def endpoint-pattern2 #"^([^ ]*) ([^ ]*) endpoint ([^ ]*) ([^:]*):([^ ]*) ([^:]*):([^ ]*)(| on-events ([^ ]*))$")
(def endpoint-pattern3 #"^([^ ]*) ([^ ]*) endpoint ([^ ]*) ([^:]*):([^ ]*)(| on-events ([^ ]*))$")
(def endpoint-pattern4 #"^([^ ]*) ([^ ]*) endpoint ([^ ]*)(| on-events ([^ ]*))$")
(def attr-key {"body"    :body-template
               "opts"    :opts
               "headers" :headers})
(defn load-endpoints [blueprints serialized-blueprints]
  (reduce (fn gen-blueprint [blueprints blueprint]
            blueprints
            #_(if-let [[endpoint? type att url ext1 s1 ext2 s2 ext3 s3 on-events events] (re-matches endpoint-pattern blueprint)]
                (let [fully-qualified-attr (keyword (str (if (= "general" type) "" (str type \/)) att "-endpoint"))
                      [event] (when events (read-string events))]
                  (-> blueprints
                      (assoc fully-qualified-attr
                             (cond-> (endpoint url (get attr-key ext1) s1 (get attr-key ext2) s2 (get attr-key ext3) s3)
                                     event (->on-event (keyword event))))
                      (update :unhandled disj blueprint)))
                (if-let [[endpoint? type att url ext1 s1 ext2 s2 on-events events] (re-matches endpoint-pattern2 blueprint)]
                  (let [fully-qualified-attr (keyword (str (if (= "general" type) "" (str type \/)) att "-endpoint"))
                        [event] (when events (read-string events))]
                    (-> blueprints
                        (assoc fully-qualified-attr
                               (cond-> (endpoint url (get attr-key ext1) s1 (get attr-key ext2) s2)
                                       event (->on-event (keyword event))))
                        (update :unhandled disj blueprint)))
                  (if-let [[endpoint? type att url ext1 s1 on-events events] (re-matches endpoint-pattern3 blueprint)]
                    (let [fully-qualified-attr (keyword (str (if (= "general" type) "" (str type \/)) att "-endpoint"))
                          [event] (when events (read-string events))]
                      (-> blueprints
                          (assoc fully-qualified-attr
                                 (cond-> (endpoint url (get attr-key ext1) s1)
                                         event (->on-event (keyword event))))
                          (update :unhandled disj blueprint)))
                    (if-let [[endpoint? type att url on-events events] (re-matches endpoint-pattern4 blueprint)]
                      (let [fully-qualified-attr (keyword (str (if (= "general" type) "" (str type \/)) att "-endpoint"))
                            [event] (when events (read-string events))]
                        (-> blueprints
                            (assoc fully-qualified-attr
                                   (cond-> (endpoint url)
                                           event (->on-event (keyword event))))
                            (update :unhandled disj blueprint)))
                      blueprints)))))
          blueprints
          serialized-blueprints))

(def extract-pattern #"^([^ ]*) ([^ ]*) extract ([^]]*]) from (.*]])(| on-events ([^ ]*))$")
#_(defn load-extracts [blueprints serialized-blueprints]
    (reduce (fn gen-blueprint [blueprints blueprint]
              (if-let [[extract? type att extract-instruction sources on-events events] (re-matches extract-pattern blueprint)]
                (let [fully-qualified-attr (keyword (str type \/ att))
                      extract-instruction (map keyword (read-string extract-instruction))
                      sources (read-string sources)
                      [event] (when events (read-string events))]
                  (-> blueprints
                      (assoc fully-qualified-attr
                             (cond-> (extract extract-instruction sources)
                                     event (->on-event (keyword event))))
                      (update :unhandled disj blueprint)))
                blueprints))
            blueprints
            serialized-blueprints))

(defn try-resolve [type s & [ns-hint]]
  (when (some? s)
    (let [s (clojure.string/replace s "_" "-")]
      (if-let [f (resolve (read-string s))]
        (var-get f)
        (if-let [f (resolve (read-string (str ns-hint \/ s)))]
          (var-get f)
          (logger/error (str "Unresolved " ns-hint " " type " " s)))))))

(def generic-inference-pattern #"^([^ ]*) ([^ ]*) from (\[\[.*]])(?:| eval-when ([^ ]*))(?:| notify-when ([^ ]*))(?:| on-events ([^ ]*))$")
(defn load-generic-inferences [blueprints serialized-blueprints & [ns-hint]]
  (reduce (fn gen-blueprint [blueprints blueprint]
            (if-let [[extract? att infer-fn sources eval-cond-fn notify-cond-fn events] (re-matches generic-inference-pattern blueprint)]
              (let [fully-qualified-attr (keyword att)
                    infer-fn (try-resolve type infer-fn ns-hint)
                    sources (read-string sources)
                    eval-cond-fn (try-resolve type eval-cond-fn ns-hint)
                    notify-cond-fn (try-resolve type notify-cond-fn ns-hint)
                    [event] (when events (read-string events))]
                (-> blueprints
                    (assoc fully-qualified-attr
                           (cond-> (->dynamic infer-fn sources)
                                   eval-cond-fn (assoc :eval-on-condition eval-cond-fn)
                                   notify-cond-fn (assoc :notify-on-condition notify-cond-fn)
                                   event (->on-event (keyword event))))
                    (update :unhandled disj blueprint)))
              blueprints))
          blueprints
          serialized-blueprints))

(def inference-pattern #"^([^ ]*) ([^ ]*) ([^ ]*) from (\[\[.*]])(?:| eval-when ([^ ]*))(?:| notify-when ([^ ]*))(?:| on-events ([^ ]*))$")
(defn load-inferences [blueprints serialized-blueprints & [ns-hint]]
  (reduce (fn gen-blueprint [blueprints blueprint]
            (if-let [[extract? type att infer-fn sources eval-cond-fn notify-cond-fn events] (re-matches inference-pattern blueprint)]
              (let [fully-qualified-attr (keyword (str type \/ att))
                    infer-fn (try-resolve type infer-fn ns-hint)
                    sources (read-string sources)
                    eval-cond-fn (or (try-resolve type eval-cond-fn ns-hint) (constantly true))
                    notify-cond-fn (try-resolve type notify-cond-fn ns-hint)
                    [event] (when events (read-string events))]
                (if (get blueprints fully-qualified-attr)
                  (-> blueprints
                      (update fully-qualified-attr
                              merge (cond-> {:eval-fn infer-fn :sources sources}
                                            eval-cond-fn (assoc :eval-on-condition eval-cond-fn)
                                            notify-cond-fn (assoc :notify-on-condition notify-cond-fn)
                                            event (->on-event (keyword event))))
                      (update :unhandled disj blueprint))
                  (-> blueprints
                      (assoc fully-qualified-attr
                             (cond-> (->dynamic infer-fn sources)
                                     eval-cond-fn (assoc :eval-on-condition eval-cond-fn)
                                     notify-cond-fn (assoc :notify-on-condition notify-cond-fn)
                                     event (->on-event (keyword event))))
                      (update :unhandled disj blueprint))))
              blueprints))
          blueprints
          serialized-blueprints))

(defn resolve-sources [blueprints]
  (into {}
        (map (fn [[k {:keys [sources type] :as attribute-data}]]
               [k (if-let [sources' (not-empty
                                      (map (fn [path]
                                             (let [[_ path] (reduce (fn [[type path] source]
                                                                      (let [attr (keyword (str type \/ source))
                                                                            attr (if (contains? blueprints attr) attr (keyword source))
                                                                            {:keys [symmetric-attribute]} (get blueprints attr)
                                                                            type (when symmetric-attribute (namespace symmetric-attribute))]
                                                                        [type (conj path attr)]))
                                                                    [(namespace k) []]
                                                                    path)]
                                               path))
                                           sources))]
                    (cond-> attribute-data
                            :sources (assoc :sources sources')
                            (not= :endpoint type) (assoc :notifiers sources'))
                    (cond-> attribute-data
                            sources (assoc :notifiers sources)))])
             blueprints)))

(defn import-blueprints [serialized-blueprints & [ns-hint]]
  (let [serialized-blueprints (->> serialized-blueprints
                                   (map clojure.string/lower-case)
                                   (map #(clojure.string/replace % #" can be " " has "))
                                   (map #(clojure.string/replace % #" can " " has "))
                                   (map #(clojure.string/replace % #" a " " "))
                                   (map #(clojure.string/replace % #"^a " ""))
                                   (map #(clojure.string/replace % #"^an " ""))
                                   (map #(clojure.string/replace % #"," ""))
                                   (map #(clojure.string/replace % #"evaluated with" ""))
                                   (map #(clojure.string/replace % #"evaluated by" ""))
                                   (map #(clojure.string/replace % #"evaluated via" ""))
                                   )
        blueprints (-> {:unhandled (set serialized-blueprints)}
                       (load-relations serialized-blueprints)
                       (load-statics serialized-blueprints)
                       (load-static-generics serialized-blueprints)
                       (load-endpoints serialized-blueprints)
                       ;(load-extracts serialized-blueprints)
                       (load-generic-inferences serialized-blueprints ns-hint)
                       (load-inferences serialized-blueprints ns-hint))]
    (if-let [unhandled (not-empty (:unhandled blueprints))]
      (logger/error (str "Unhandled: " unhandled))
      (resolve-sources (dissoc blueprints :unhandled)))))
