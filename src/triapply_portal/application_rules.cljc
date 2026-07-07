(ns triapply-portal.application-rules
  (:require [clojure.string :as str]))

(defn values
  "Normalizes a missing, scalar, or repeated form value to a vector."
  [value]
  (cond
    (nil? value) []
    (sequential? value) (vec value)
    :else [value]))

(defn present?
  "Returns true when a form value contains meaningful user input."
  [value]
  (cond
    (string? value) (not (str/blank? value))
    (map? value) (and (present? (:filename value))
                      (pos? (or (:size value) 0)))
    (sequential? value) (boolean (some present? value))
    :else (some? value)))

(defn triggered?
  "Returns true when any selected section is in a supplemental's trigger set."
  [selected triggers]
  (boolean (some (set selected) triggers)))

(defn selection-state [selected limit]
  (let [selected (set selected)
        selected-count (count selected)]
    {:selected selected
     :selected-count selected-count
     :valid? (<= 1 selected-count limit)
     :limit-reached? (>= selected-count limit)}))
