(ns triapply-portal.client.application-form
  (:require
   [clojure.string :as str]
   [triapply-portal.application-rules :as rules]))

(defn- elements
  ([selector]
   (elements js/document selector))
  ([root selector]
   (array-seq (.querySelectorAll root selector))))

;; Draft autosave --------------------------------------------------------------
;; Keeps typed-in progress across accidental refresh/close/navigation by mirroring
;; the form into localStorage. File inputs can't be restored (browser security)
;; and the hidden submission-id is per-render, so both are skipped. Cleared on a
;; successful submission by a small script on the "received" page — the key here
;; must match the one used there.
(def ^:private storage-key "triapply-application-v1")

(defn- toggle-control? [^js el]
  (let [type (.-type el)]
    (or (= "checkbox" type) (= "radio" type))))

(defn- persistable? [^js el]
  (let [type (.-type el)]
    (and (not (contains? #{"hidden" "file" "submit" "button" "reset"} type))
         (seq (.-name el)))))

(defn- persistable-controls [form]
  (filter persistable? (elements form "input, textarea, select")))

(defn- control-key [^js el]
  ;; Checkboxes/radios share a name across values, so key those by name+value;
  ;; everything else is unique per name.
  (if (toggle-control? el)
    (str (.-name el) "::" (.-value el))
    (.-name el)))

(defn- save-draft! [form]
  (let [state (reduce (fn [acc ^js el]
                        (assoc acc (control-key el)
                               (if (toggle-control? el) (.-checked el) (.-value el))))
                      {}
                      (persistable-controls form))]
    (try
      (.setItem js/localStorage storage-key (js/JSON.stringify (clj->js state)))
      (catch :default _ nil))))

(defn- restore-draft! [form]
  (when-let [raw (try (.getItem js/localStorage storage-key) (catch :default _ nil))]
    (when-let [state (try (js->clj (js/JSON.parse raw)) (catch :default _ nil))]
      (doseq [^js el (persistable-controls form)
              :let [k (control-key el)]
              :when (contains? state k)]
        (if (toggle-control? el)
          (set! (.-checked el) (boolean (get state k)))
          (set! (.-value el) (get state k)))))))

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

;; Date fields -----------------------------------------------------------------
;; Track empty vs. filled so the CSS can mute the mm/dd/yyyy format hint, and let
;; a click anywhere on the field open the native picker (not just the tiny icon).
(defn- sync-date-value! [^js el]
  (.toggle (.-classList el) "has-value" (not (str/blank? (.-value el)))))

(defn- init-date-fields! []
  (doseq [^js el (elements ".triangle-date")]
    (sync-date-value! el)
    (.addEventListener el "input" #(sync-date-value! el))
    (.addEventListener el "change" #(sync-date-value! el))))

(defn init []
  (let [form (.querySelector js/document "form")
        section-boxes (elements ".section-interest")
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
      ;; Repopulate any saved draft before deriving section counts / visibility,
      ;; so restored checkboxes drive the rest of the UI on load.
      (when form
        (restore-draft! form)
        (.addEventListener form "input" #(save-draft! form))
        (.addEventListener form "change" #(save-draft! form)))
      (doseq [box section-boxes]
        (.addEventListener box "change" update-form!))
      (doseq [radio option-radios]
        (.addEventListener radio "change" update-form!))
      (doseq [field conditional-fields]
        (.addEventListener field "input" update-form!)
        (.addEventListener field "change" update-form!))
      (init-date-fields!)
      (update-form!))))
