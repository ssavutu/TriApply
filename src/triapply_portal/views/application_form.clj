(ns triapply-portal.views.application-form
  (:require
   [clojure.string :as str]
   [triapply-portal.application-config :as config]
   [triapply-portal.views.layout :as layout]))

(def field-class
  "mt-3 block w-full border-0 border-b border-triangleGray bg-transparent px-0 py-2 text-base text-triangleDark placeholder:text-triangleDark/40 focus:border-triangleBlue focus:outline-none focus:ring-0")

(def file-field-class
  "mt-3 block w-full text-sm text-triangleDark file:mr-4 file:rounded-md file:border-0 file:bg-triangleBlue/10 file:px-4 file:py-2 file:text-sm file:font-semibold file:text-triangleBlue hover:file:bg-triangleBlue/15")

(def detail-field-class
  "block flex-1 border-0 border-b border-triangleGray bg-transparent px-0 py-1 text-base text-triangleDark placeholder:text-triangleDark/40 focus:border-triangleBlue focus:outline-none focus:ring-0")

;; Native date input, themed to match `field-class`. The `triangle-date` marker
;; class drives the CSS that recolors the calendar icon and the click-to-open JS.
(def date-field-class (str field-class " triangle-date cursor-pointer"))

(def card-class
  "max-w-2xl mx-auto bg-white rounded-xl border border-blue-600 p-8 sm:p-10 font-roboto-slab")

(def first-card-class (str card-class " mt-10"))
(def stacked-card-class (str card-class " mt-8"))
(def conditional-card-class
  (str "supplemental-section hidden " stacked-card-class " border-l-4 space-y-4"))
(def card-heading-class
  "inline-block font-roboto-slab text-2xl font-extrabold text-triangleDark border-b border-blue-600 pb-2")

(defn content-block [{:keys [type text items variant]}]
  (case type
    :heading
    [:h3 {:class "mt-6 font-roboto-slab text-xl font-extrabold uppercase"} text]

    :paragraph
    [:p {:class (str "mt-3 text-triangleDark/80"
                     (when (= :note variant) " italic"))}
     text]

    :ordered-list
    (into [:ol {:class "mt-3 list-decimal space-y-2 pl-5 text-triangleDark/80"}]
          (map (fn [item] [:li item]) items))

    :unordered-list
    (into [:ul {:class "mt-3 list-disc space-y-2 pl-5 text-triangleDark/80"}]
          (map (fn [item] [:li item]) items))

    (throw (ex-info "Unknown application content block type." {:type type}))))

(defn required-mark [required?]
  (when required?
    [:span {:class "ml-1 text-red-600"} "*"]))

(defn input-control
  ([field] (input-control field {}))
  ([{nm :name :keys [type id placeholder autocomplete required? rows multiple accept aria-label]}
    extra-attributes]
   (let [attributes (merge
                     {:id id
                      :name nm
                      :placeholder placeholder
                      :autocomplete autocomplete
                      :aria-label aria-label
                      :required required?}
                     extra-attributes)]
     (case type
       :textarea
       [:textarea (assoc attributes
                         :class (or (:class attributes)
                                    (str field-class " min-h-28 resize-y"))
                         :rows (or rows 4))]

       :file
       [:input (assoc attributes
                      :class (or (:class attributes) file-field-class)
                      :type "file"
                      :accept accept
                      :multiple multiple)]

       :date
       [:input (assoc attributes
                      :class (or (:class attributes) date-field-class)
                      :type "date")]

       [:input (assoc attributes
                      :class (or (:class attributes) field-class)
                      :type (name type))]))))

(defn radio-option [group-name group-required? {:keys [id value label detail-field]}]
  [:label {:class "flex items-center gap-3 text-base text-triangleDark"}
   [:input {:class "h-5 w-5 border-triangleGray text-triangleBlue focus:ring-triangleBlue"
            :type "radio"
            :id id
            :name group-name
            :value value
            :required group-required?
            :data-option-trigger true}]
   [:span label]
   (when detail-field
     (input-control
      detail-field
      {:class detail-field-class
       :disabled true
       :required false
       :data-option-detail true
       :data-trigger-id id
       :data-required (when (:required? detail-field) "true")}))])

(defn applicant-field [{:keys [type label required? width options] :as field}]
  (let [attributes (if (= :full width) {:class "sm:col-span-2"} {})]
    (if (= :radio-group type)
      [:div attributes
       [:fieldset
        [:legend {:class "text-base font-medium text-triangleDark"}
         label (required-mark required?)]
        (into [:div {:class "mt-4 space-y-4"}]
              (map #(radio-option (:name field) required? %) options))]]
      [:div attributes
       [:label {:class "block text-base font-medium text-triangleDark" :for (:id field)}
        label (required-mark required?)]
       (input-control field)])))

(defn section-checkbox [{:keys [value label]}]
  [:label {:class "flex items-center gap-3 text-base text-triangleDark"}
   [:input {:class "section-interest triangle-checkbox disabled:opacity-40"
            :type "checkbox"
            :name "section-interest"
            :value value}]
   label])

(defn supplemental-field [{:keys [label required?] :as field}]
  [:div
   [:label {:class "block text-base font-medium text-triangleDark" :for (:id field)}
    label (required-mark required?)]
   (input-control
    field
    {:disabled true
     :required false
     :data-conditional-field true
     :data-required (when required? "true")})])

(defn require-any-note
  "A visible cue for a `require-any` group: the fields are individually optional,
  but the applicant must complete at least one — so neither field shows an
  asterisk on its own. Without this the group reads as fully optional."
  [require-any]
  (when (seq require-any)
    [:p {:class "mt-3 text-base font-medium text-triangleDark"}
     [:span {:class "text-red-600"} "*"]
     " At least one of the following is required."]))

(defn supplemental-section
  [{:keys [id title content fields triggers require-any require-any-error]}]
  (into
   (cond-> [:section {:class conditional-card-class
                      :data-supplemental id
                      :data-triggers (str/join " " (sort triggers))
                      :data-require-any (when (seq require-any)
                                          (str/join " " (sort require-any)))
                      :data-require-any-error require-any-error}]
     title (conj [:h2 {:class card-heading-class} title]))
   (concat (map content-block content)
           (when-let [note (require-any-note require-any)] [note])
           (map supplemental-field fields))))

(defn application-page
  ([] (application-page config/membership-application))
  ([{:keys [page introduction applicant section-limit section-picker sections
            preference-field supplemental-empty supplementals submit]}]
   (layout/page
    (:browser-title page)
    [:header {:class "mx-auto mt-10 max-w-2xl border-b-2 border-black pb-5 text-center"}
     [:img {:class "mx-auto w-full max-w-md"
            :src (get-in page [:masthead :src])
            :alt (get-in page [:masthead :alt])}]]
    (into
     [:section {:class first-card-class}
      [:h1 {:class "mb-4 font-playfair text-4xl font-bold"}
       (:application-title page)]]
     (map content-block introduction))
    [:form {:action "/applications"
            :method "post"
            :enctype "multipart/form-data"
            :class "font-roboto-slab"}
     ;; Stable per-render idempotency key: a browser resubmit of this same form
     ;; carries the same id, so downstream sinks can de-duplicate on retries.
     [:input {:type "hidden" :name "submission-id" :value (str (random-uuid))}]
     [:section {:class first-card-class}
      [:h2 {:class (str "mt-2 " card-heading-class)} (:title applicant)]
      (into [:div {:class "mt-8 grid gap-x-6 gap-y-7 sm:grid-cols-2"}]
            (map applicant-field (:fields applicant)))]

     [:section {:class stacked-card-class}
      [:fieldset
       [:legend {:class card-heading-class}
        (format (:title section-picker) section-limit)
        (required-mark true)]
       [:p {:class "mt-2 text-sm text-triangleDark/60"}
        (format (:help section-picker) section-limit)]
       [:p {:id "section-count"
            :data-section-limit section-limit
            :data-required-error (:required-error section-picker)
            :class "mt-4 text-sm font-medium text-triangleDark/80"}
        (str "0 of " section-limit " selected")]
       (into [:div {:class "mt-5 grid gap-4 sm:grid-cols-2"}]
             (map section-checkbox sections))]]

     [:section {:class stacked-card-class}
      (applicant-field (assoc preference-field :width :full))]

     [:section {:class (str "supplemental-empty " stacked-card-class)}
      [:h2 {:class card-heading-class} (:title supplemental-empty)]
      [:p {:class "mt-2 text-sm text-triangleDark/60"} (:help supplemental-empty)]]

     (map supplemental-section supplementals)

     [:section {:class (str stacked-card-class " mb-10")}
      [:button {:class "w-full rounded-md bg-triangleBlue px-5 py-3 text-base font-bold text-white hover:bg-blue-700 focus:outline-none focus:ring-2 focus:ring-triangleBlue focus:ring-offset-2"
                :type "submit"}
       (:label submit)]]

     [:script {:src "/js/application-form.js" :defer true}]])))
