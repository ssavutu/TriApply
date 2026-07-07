(ns triapply-portal.application-config
  (:require
   [clojure.edn :as edn]
   [clojure.java.io :as io]))

(def membership-application-resource "triapply_portal/membership-application.edn")

(def supported-content-types
  #{:heading :paragraph :ordered-list :unordered-list})

(def supported-field-types
  #{:text :date :tel :email :url :textarea :file :radio-group})

(def supported-validation-rules
  #{:drexel-email :past-date :four-digit-year})

(defn applicant-fields [config]
  (get-in config [:applicant :fields]))

(defn option-detail-fields [config]
  (for [field (applicant-fields config)
        option (:options field)
        :let [detail (:detail-field option)]
        :when detail]
    detail))

(defn main-fields [config]
  (concat (applicant-fields config)
          [(:preference-field config)]))

(defn answer-fields [config]
  (concat (main-fields config) (option-detail-fields config)))

(defn supplemental-fields [config]
  (mapcat :fields (:supplementals config)))

(defn all-fields [config]
  (concat (answer-fields config) (supplemental-fields config)))

(defn content-blocks [config]
  (concat (:introduction config)
          (mapcat :content (:supplementals config))))

(defn- valid-content-block? [{:keys [type text items]}]
  (and (contains? supported-content-types type)
       (case type
         (:heading :paragraph) (string? text)
         (:ordered-list :unordered-list) (and (seq items) (every? string? items))
         false)))

(defn validate-config [config]
  (let [sections (map :value (:sections config))
        section-values (set sections)
        fields (all-fields config)
        field-names (map :name fields)]
    (when-not (= 1 (:version config))
      (throw (ex-info "Unsupported application form config version."
                      {:version (:version config)})))
    (when-not (and (pos-int? (:section-limit config))
                   (seq sections)
                   (<= (:section-limit config) (count sections)))
      (throw (ex-info "The application form needs sections and a positive limit." {})))
    (when-not (= (count sections) (count section-values))
      (throw (ex-info "Section values must be unique." {})))
    (when-not (= (count field-names) (count (set field-names)))
      (throw (ex-info "Form field names must be unique." {})))
    (when-not (every? #(and (string? (:name %))
                            (contains? supported-field-types (:type %)))
                      fields)
      (throw (ex-info "Every form field needs a name and supported type." {})))
    (when-not (every? valid-content-block? (content-blocks config))
      (throw (ex-info "The form contains an unsupported content block." {})))
    (when-not (every? #(or (nil? (:validation %))
                           (contains? supported-validation-rules
                                      (get-in % [:validation :rule])))
                      fields)
      (throw (ex-info "The form contains an unsupported validation rule." {})))
    (doseq [{:keys [name options]} (filter #(= :radio-group (:type %)) fields)]
      (let [ids (map :id options)
            values (map :value options)]
        (when-not (and (seq options)
                       (every? string? ids)
                       (every? string? values)
                       (= (count ids) (count (set ids)))
                       (= (count values) (count (set values))))
          (throw (ex-info "Radio options need unique string IDs and values."
                          {:field name})))))
    (doseq [{:keys [id triggers require-any fields]} (:supplementals config)]
      (when-not (every? section-values triggers)
        (throw (ex-info "A supplemental references an unknown section."
                        {:supplemental id :triggers triggers})))
      (when-not (every? (set (map :name fields)) require-any)
        (throw (ex-info "A supplemental require-any group references an unknown field."
                        {:supplemental id :require-any require-any}))))
    config))

(defn read-config
  ([] (read-config membership-application-resource))
  ([resource-name]
   (let [resource (io/resource resource-name)]
     (when-not resource
       (throw (ex-info "Application form config was not found."
                       {:resource resource-name})))
     (with-open [reader (java.io.PushbackReader. (io/reader resource))]
       (-> (edn/read {:eof nil} reader)
           validate-config)))))

(def membership-application
  (read-config))
