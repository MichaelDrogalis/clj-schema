(ns ^{:doc
      "Define validation schemas for validating maps.

      Schemas are any number of paths through a nested map, paired with a validator.

      Validators are either a single predicate or schema, or a seq of predicates
      or schems (or a mix of predicates or schemas).

      A path may also be marked as an `optional-path`. By default a value at a given
      path is assumed to be a single value; to mark it sequential, wrap the validator
      in the 'sequence-of' function. NOTE: 'nil' is a passing value for a 'sequence-of' validator.

      Example Schema:

      [[:a :b :c] pred
       [:x :y :z] [pred2 pred3 z-schema] ;; implicit 'and' - all three must pass
       [:p :q :r] [:or nil? r-schema]    ;; an 'or' statement - need just one to pass
       (optional-path [:z]) (sequence-of string?)
       [:a b :d] (loose-valdiation-schema [[:cat :name] String ;; can use Java Class objects directly
                                           [:cat :color] String])
      ... ]

      `defschema` creates a strict schema, which expects only the paths it
      describes to be present on the given map.

      `def-loose-schema` creates a loose schema, which expects its paths to
      be present but does not complain about extra paths."}
  clj-schema.schema
  (:require [clj-schema.internal.utils :as u]))


;;;; Validation Schema Creation

(defn schema-path-set [schema]
  (set (take-nth 2 schema)))

(defn loose-schema
  "From a seq of vectors, creates a schema that can be used within other schemas.
   Checks for the presence of all paths; other paths may also exist."
  [& vs]
  {:pre [(even? (count (apply concat vs)))
         (every? vector? (schema-path-set (apply concat vs)))]}
  (let [schema-vector (vec (apply concat vs))]
    (vary-meta schema-vector assoc ::schema true)))

(defn strict-schema
  "From a seq of vectors, creates a schema that can be used within other schemas.
   Any paths found in addition to the ones specified are considered a violation."
  [& vs]
  {:pre [(even? (count (apply concat vs)))
         (every? vector? (schema-path-set (apply concat vs)))]}
  (let [schema-vector (vec (apply concat vs))]
    (vary-meta schema-vector merge {::schema true
                                    ::strict-schema true})))

(defmacro def-loose-schema
  "Creates a named var for a loose schema that can be used within other schemas."
  [name & schema-vectors]
  `(-> (def ~name (loose-schema ~@schema-vectors))
       (alter-meta! assoc ::schema true)))

(defmacro defschema
  "Creates a named var for a strict schema that can be used within other schemas."
  [name & schema-vectors]
  `(-> (def ~name (strict-schema ~@schema-vectors))
       (alter-meta! merge {::schema true
                           ::strict-schema true})))


;; Questions asked of Schemas

(defn schema? [x]
  (boolean (::schema (meta x))))

(defn strict-schema? [x]
  (boolean (and (schema? x)
             (::strict-schema (meta x)))))

(defn loose-schema? [x]
  (and (schema? x)
       (not (::strict-schema (meta x)))))

(defn schema-rows [schema]
  (partition 2 schema))

(defn num-schema-paths [schema]
  (count (schema-rows schema)))


;; Validator Modifiers

;; I can't use metadata to tag things as being validators that are meant to be run against
;; a sequential value, because Java Class objects cannot have metadata applied to them

; sequence-of
(defrecord SequenceOfItemsValidator [single-item-validator])

(defn sequence-of? [validator]
  (= SequenceOfItemsValidator (class validator)))

(defn sequence-of [single-item-validator]
  (SequenceOfItemsValidator. single-item-validator))

; wild
(defrecord WildcardValidator [validator])

(defn wildcard-validator? [validator]
  (= WildcardValidator (class validator)))

(defn wild
  "Upgrades a validator to be used within a path as a wildcard.
   Ex. [:a (wild Integer) (wild String)], matches all paths like [:a 1 \"product-1\"] or [:a 42 \"product-2\"]"
  [validator]
  (WildcardValidator. validator))

(defn wildcard-path? [schema-path]
  (some wildcard-validator? schema-path))

(defn wildcard-paths [schema]
  (filter wildcard-path? (schema-path-set schema)))


;;;; Schema Path Modifiers

(defn optional-path
  "Takes a schema path and morphs it into a path that is optional.
   Optional paths may or may not be present on the validated map, but
   if they are present they must be valid against the given validator."
  [schema-path]
  (vary-meta schema-path assoc ::optional-path true))

(defn optional-path? [schema-path]
  (boolean (::optional-path (meta schema-path))))

(defn optional-path-set [schema]
  (clojure.set/select optional-path? (schema-path-set schema)))


;; Filtering Schemas

(defn filter-schema
  "Takes a pred like (fn [[path validator]] ...) and selects all schema rows that match."
  [pred schema]
  (let [new-schema (->> (schema-rows schema)
                        (filter pred)
                        (apply concat)
                        vec)]
    (with-meta new-schema (meta schema))))

(defn subtract-paths
  "Returns a new schema minus some paths."
  [schema & paths]
  (filter-schema (fn [[path validator]] (not (contains? (set paths) path)))
                     schema))

(defn select-schema-keys
  "Returns a new schema with only the paths starting with the specified keys."
  [schema & ks]
  (filter-schema (fn [[path validator]] (contains? (set ks) (first path)))
                      schema))

(defn subtract-wildcard-paths
  "Returns a schema that is the same in all respects, except it has none of the wildcard paths."
  [schema]
  (filter-schema (fn [[path validator]] (not (wildcard-path? path)))
                      schema))


;;;; Namespace Info

(defn ns->schemas
  "All schemas in a namespace"
  [the-ns]
  (filter schema? (vals (ns-interns the-ns))))

