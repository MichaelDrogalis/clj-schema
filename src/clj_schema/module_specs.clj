(ns clj-schema.module-specs
  "Aspect-oriented function input/output schema validations, intended
   to be used to test the module boundaries of your application"
  (:use [clj-schema.schema :refer [def-map-schema optional-path schema? sequence-of]]
        [clj-schema.simple-schemas :refer [Anything]]
        [clj-schema.validation :refer [validation-errors]])
  (:require [robert.hooke :as hooke]))


(def-map-schema :loose module-spec-schema
  [(optional-path [:module-entry-point-var]) var?
   (optional-path [:input-schema]) Anything
   (optional-path [:input-schema-on-failure]) fn?
   (optional-path [:output-schema]) Anything
   (optional-path [:output-schema-on-failure]) fn?])

(defn- schema-checker-fn [{:keys [module-entry-point-var
                                  input-schema
                                  input-schema-on-failure
                                  input-schema-on-success
                                  output-schema
                                  output-schema-on-failure
                                  output-schema-on-success]}]
  (fn [f & args]
    (let [errors (and input-schema (validation-errors input-schema args))]
      (if (seq errors)
        (if input-schema-on-failure
          (input-schema-on-failure module-entry-point-var (vec args) errors)
          (throw (Exception. (str "Errors found in inputs, " (vec args) ", to " module-entry-point-var ": " errors))))
        (when input-schema-on-success
          (input-schema-on-success module-entry-point-var (vec args)))))

    (let [result (apply f args)
          errors (and output-schema (validation-errors output-schema result))]
      (if (seq errors)
        (if output-schema-on-failure
          (output-schema-on-failure module-entry-point-var result errors)
          (throw (Exception. (str "Errors found in outputs, " result ", from " module-entry-point-var ": " errors))))
        (when output-schema-on-success
          (output-schema-on-success module-entry-point-var result)))
      result)))

(defn add-module-specs-hooks! [specifications]
  (when-let [errors (seq (validation-errors (sequence-of module-spec-schema) specifications))]
    (throw (Exception. (str "specifications were not valid: " specifications errors))))
  (doseq [spec specifications]
    (hooke/add-hook (:module-entry-point-var spec) ::spec-hook (schema-checker-fn spec))))

(defn remove-module-spec-hooks! [specifications]
  (when-let [errors (seq (validation-errors (sequence-of module-spec-schema) specifications))]
    (throw (Exception. (str "specifications were not valid: " specifications errors))))
  (doseq [spec specifications]
    (hooke/remove-hook (:module-entry-point-var spec) ::spec-hook)))