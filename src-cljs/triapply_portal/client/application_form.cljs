(ns triapply-portal.client.application-form
  (:require
   [clojure.string :as str]
   [triapply-portal.application-rules :as rules]))

(defn- elements
  ([selector]
   (elements js/document selector))
  ([root selector]
   (array-seq (.querySelectorAll root selector))))

(defn- selected-values [section-boxes]
  (into #{}
        (comp (filter #(.-checked %))
              (map #(.-value %)))
        section-boxes))

(defn- data-values [^js element property]
  (let [value (or (aget (.-dataset element) property) "")]
    (remove str/blank? (str/split value #"\s+"))))

(defn- field-present? [^js field]
  (if (= "file" (.-type field))
    (pos? (.. field -files -length))
    (not (str/blank? (.-value field)))))

(defn- set-group-validity! [^js section show?]
  (let [required-names (set (data-values section "requireAny"))
        fields (filter #(contains? required-names (.-name %))
                       (elements section "[data-conditional-field]"))
        valid? (or (not show?) (some field-present? fields))]
    (doseq [field fields]
      (.setCustomValidity field ""))
    (when (and show? (not valid?) (seq fields))
      (.setCustomValidity (first fields)
                          (or (.. section -dataset -requireAnyError)
                              "Complete at least one field.")))))

(defn- set-supplemental-visibility! [section show?]
  (.toggle (.-classList section) "hidden" (not show?))
  (doseq [field (elements section "[data-conditional-field]")]
    (set! (.-disabled field) (not show?))
    (set! (.-required field)
          (and show? (= "true" (.. field -dataset -required))))
    (when-not show?
      (.setCustomValidity field "")))
  (set-group-validity! section show?))

(defn- update-supplementals! [section-boxes supplemental-sections supplemental-empty]
  (let [selected (selected-values section-boxes)]
    (doseq [section supplemental-sections]
      (set-supplemental-visibility!
       section
       (rules/triggered? selected (data-values section "triggers"))))

    (let [any-visible? (some #(not (.contains (.-classList %) "hidden"))
                             supplemental-sections)]
      (.toggle (.-classList supplemental-empty) "hidden" (boolean any-visible?)))))

(defn- update-option-details! [detail-fields]
  (doseq [^js detail detail-fields]
    (let [trigger (.getElementById js/document (.. detail -dataset -triggerId))
          selected? (and trigger (.-checked trigger))]
      (set! (.-disabled detail) (not selected?))
      (set! (.-required detail)
            (and selected? (= "true" (.. detail -dataset -required)))))))

(defn init []
  (let [section-boxes (elements ".section-interest")
        section-count (.getElementById js/document "section-count")
        supplemental-empty (.querySelector js/document ".supplemental-empty")
        supplemental-sections (elements ".supplemental-section")
        conditional-fields (elements "[data-conditional-field]")
        option-radios (elements "[data-option-trigger]")
        option-details (elements "[data-option-detail]")
        configured-limit (js/Number (.. section-count -dataset -sectionLimit))
        section-limit (if (pos? configured-limit)
                        configured-limit
                        (count section-boxes))]
    (letfn [(update-form! []
              (let [{:keys [selected-count limit-reached?]}
                    (rules/selection-state (selected-values section-boxes)
                                           section-limit)]
                (set! (.-textContent section-count)
                      (str selected-count " of " section-limit " selected"))
                (.setCustomValidity (first section-boxes)
                                    (if (zero? selected-count)
                                      (.. section-count -dataset -requiredError)
                                      ""))
                (doseq [box section-boxes]
                  (set! (.-disabled box)
                        (and (not (.-checked box)) limit-reached?)))
                (update-option-details! option-details)
                (update-supplementals!
                 section-boxes supplemental-sections supplemental-empty)))]
      (doseq [box section-boxes]
        (.addEventListener box "change" update-form!))
      (doseq [radio option-radios]
        (.addEventListener radio "change" update-form!))
      (doseq [field conditional-fields]
        (.addEventListener field "input" update-form!)
        (.addEventListener field "change" update-form!))
      (update-form!))))
