(ns triapply-portal.application-submission
  (:require
   [clojure.string :as str]
   [triapply-portal.application-config :as config]
   [triapply-portal.application-rules :as rules])
  (:import
   [java.io File FileInputStream]
   [java.net URI]
   [java.nio.charset StandardCharsets]
   [java.time LocalDate]))

(defn- add-error [errors field message]
  (if (contains? errors field)
    errors
    (assoc errors field message)))

(defn- text-present? [value]
  (and (string? value) (rules/present? value)))

(defn- valid-date? [value]
  (try
    (let [date (when (string? value) (LocalDate/parse value))]
      (and date (not (.isAfter date (LocalDate/now)))))
    (catch Exception _ false)))

(defn- valid-url? [value]
  (try
    (let [uri (when (string? value) (URI. value))]
      (and (contains? #{"http" "https"} (some-> (.getScheme uri) str/lower-case))
           (some? (.getHost uri))))
    (catch Exception _ false)))

(defn- pdf-signature? [{:keys [tempfile]}]
  (when (instance? File tempfile)
    (with-open [stream (FileInputStream. ^File tempfile)]
      (let [header (byte-array 5)
            bytes-read (.read stream header)]
        (and (= 5 bytes-read)
             (= "%PDF-" (String. header StandardCharsets/US_ASCII)))))))

(defn- pdf-upload? [{:keys [filename content-type] :as upload}]
  (and (rules/present? upload)
       (string? filename)
       (string? content-type)
       (str/ends-with? (str/lower-case filename) ".pdf")
       (= "application/pdf"
          (some-> content-type (str/split #";" 2) first str/lower-case))
       (pdf-signature? upload)))

(defn- validation-rule-valid? [rule value]
  (case rule
    :drexel-email
    (and (string? value)
         (boolean (re-matches #"(?i)^[^@\s]+@drexel\.edu$" value)))

    :past-date
    (valid-date? value)

    :four-digit-year
    (and (string? value) (boolean (re-matches #"\d{4}" value)))

    true))

(defn- validate-field [errors params {:keys [type name label required? error
                                              validation] :as field}]
  (let [value (get params name)
        file? (= :file type)
        required-value? (if file? (rules/present? value) (text-present? value))]
    (cond-> errors
      (and required? (not required-value?))
      (add-error name (or error (str label " is required.")))

      (and file?
           (rules/present? value)
           (not-every? pdf-upload? (rules/values value)))
      (add-error name (or (:invalid-error field)
                          (str label " must contain valid PDF files.")))

      (and (= :url type)
           (rules/present? value)
           (not (valid-url? value)))
      (add-error name (or (:invalid-error field)
                          (str label " must be a valid HTTP or HTTPS URL.")))

      (and validation
           (rules/present? value)
           (not (validation-rule-valid? (:rule validation) value)))
      (add-error name (:message validation)))))

(defn- validate-radio-group [errors params {:keys [name required? error options]}]
  (let [value (get params name)
        option (some #(when (= value (:value %)) %) options)
        errors (if (and required? (nil? option))
                 (add-error errors name error)
                 errors)]
    (if-let [detail-field (:detail-field option)]
      (validate-field errors params detail-field)
      errors)))

(defn- validate-main-fields [errors config params]
  (reduce
   (fn [errors field]
     (if (= :radio-group (:type field))
       (validate-radio-group errors params field)
       (validate-field errors params field)))
   errors
   (config/main-fields config)))

(defn- validate-sections [errors config params]
  (let [selected (set (rules/values (get params "section-interest")))
        section-limit (:section-limit config)
        section-values (set (map :value (:sections config)))
        picker (:section-picker config)]
    (cond-> errors
      (empty? selected)
      (add-error "section-interest" (:required-error picker))

      (> (count selected) section-limit)
      (add-error "section-interest" (format (:limit-error picker) section-limit))

      (seq (remove section-values selected))
      (add-error "section-interest"
                 (or (:invalid-error picker)
                     "One or more selected sections are invalid.")))))

(defn- validate-supplementals [errors config params]
  (let [selected (set (rules/values (get params "section-interest")))]
    (reduce
     (fn [errors {:keys [triggers fields require-any require-any-error]}]
       (if-not (rules/triggered? selected triggers)
         errors
         (let [errors (reduce #(validate-field %1 params %2) errors fields)]
           (if (and (seq require-any)
                    (not (some #(rules/present? (get params %)) require-any)))
             (add-error errors (first (sort require-any)) require-any-error)
             errors))))
     errors
     (:supplementals config))))

(defn validate
  "Returns a field-to-message map. An empty map means the request is valid."
  ([params] (validate config/membership-application params))
  ([config params]
   (-> {}
       (validate-main-fields config params)
       (validate-sections config params)
       (validate-supplementals config params))))

(defn- upload-map? [v]
  (and (map? v) (contains? v :tempfile)))

(defn- prune-empty-uploads
  "Drops empty file parts (a blank filename / zero-byte tempfile that the browser
  sends for an untouched file input). A single empty upload becomes nil; empties
  are filtered out of multi-file vectors. Non-upload values pass through."
  [value]
  (cond
    (upload-map? value) (when (rules/present? value) value)
    (sequential? value) (vec (keep prune-empty-uploads value))
    :else value))

(defn normalized
  ([params] (normalized config/membership-application params))
  ([config params]
   {:answers (into {}
                   (map (fn [{:keys [name]}]
                          [name (prune-empty-uploads (get params name))]))
                   (config/answer-fields config))
    :sections (rules/values (get params "section-interest"))
    :supplementals (into {}
                         (keep (fn [{:keys [name]}]
                                 (let [v (prune-empty-uploads (get params name))]
                                   (when-not (or (nil? v)
                                                 (and (sequential? v) (empty? v)))
                                     [name v]))))
                         (config/supplemental-fields config))}))
