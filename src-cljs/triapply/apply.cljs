(ns triapply.apply
  (:require [clojure.string :as str]))

(defn- elements [selector]
  (array-seq (.querySelectorAll js/document selector)))

(defn- selected-values [section-boxes]
  (into #{}
        (comp (filter #(.-checked %))
              (map #(.-value %)))
        section-boxes))

(defn- triggers-for [^js section]
  (let [triggers (or (.. section -dataset -triggers) "")]
    (remove str/blank? (str/split triggers #"\s+"))))

(defn- set-supplemental-visibility! [section show?]
  (.toggle (.-classList section) "hidden" (not show?))
  (doseq [field (array-seq
                 (.querySelectorAll section "[data-conditional-required]"))]
    (set! (.-required field) show?)
    (when-not show?
      (set! (.-value field) ""))))

(defn- update-supplementals! [section-boxes supplemental-sections supplemental-empty]
  (let [selected (selected-values section-boxes)]
    (doseq [section supplemental-sections]
      (set-supplemental-visibility!
       section
       (boolean (some #(contains? selected %) (triggers-for section)))))

    (let [any-visible? (some #(not (.contains (.-classList %) "hidden"))
                             supplemental-sections)]
      (.toggle (.-classList supplemental-empty) "hidden" (boolean any-visible?)))))

(defn init []
  (let [section-boxes (elements ".section-interest")
        section-count (.getElementById js/document "section-count")
        supplemental-empty (.querySelector js/document ".supplemental-empty")
        supplemental-sections (elements ".supplemental-section")
        configured-limit (js/Number (.. section-count -dataset -sectionLimit))
        section-limit (if (pos? configured-limit)
                        configured-limit
                        (count section-boxes))]
    (letfn [(update-section-limit! []
              (let [selected-count (count (filter #(.-checked %) section-boxes))]
                (set! (.-textContent section-count)
                      (str selected-count " of " section-limit " selected"))
                (.setCustomValidity (first section-boxes)
                                    (if (zero? selected-count)
                                      "Pick at least one section."
                                      ""))
                (doseq [box section-boxes]
                  (set! (.-disabled box)
                        (and (not (.-checked box))
                             (>= selected-count section-limit))))
                (update-supplementals!
                 section-boxes supplemental-sections supplemental-empty)))]
      (doseq [box section-boxes]
        (.addEventListener box "change" update-section-limit!))
      (update-section-limit!))))
