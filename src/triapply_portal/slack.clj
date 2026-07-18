(ns triapply-portal.slack
  "Slack interactivity: message blocks with Accept/Deny buttons, request-signature
  verification, and the modal round-trip that collects a position name and
  triggers the applicant decision email."
  (:require
   [clojure.data.json :as json]
   [clojure.string :as str]
   [triapply-portal.application-config :as config])
  (:import
   [java.net URI]
   [java.net.http HttpClient HttpRequest HttpRequest$BodyPublishers
    HttpResponse$BodyHandlers]
   [java.nio.charset StandardCharsets]
   [java.security MessageDigest]
   [java.time Duration]
   [javax.crypto Mac]
   [javax.crypto.spec SecretKeySpec]))

(def accept-action "application-accept")
(def deny-action "application-deny")
(def modal-callback "review-decision")
(def position-block "position-block")
(def position-input "position")

;; ---------------------------------------------------------------------------
;; Outbound message
;; ---------------------------------------------------------------------------

(def ^:private max-section-chars 2900) ; Slack section text limit is 3000.
(def ^:private max-value-chars 600)

(defn- present? [v] (and (string? v) (not (str/blank? v))))

(defn- applicant-name [answers]
  (let [n (get answers "name")]
    (if (str/blank? n) "New application" n)))

(defn- truncate [s]
  (if (> (count s) max-value-chars) (str (subs s 0 max-value-chars) "…") s))

(defn- upload? [v]
  (and (map? v) (or (:tempfile v) (contains? v :filename))))

(defn- upload->md
  "Renders an uploaded-file value as a Slack link (or filename if no URL yet).
  Handles multi-file fields (a sequence of uploads)."
  [v]
  (cond
    (upload? v) (if (:url v)
                  (str "<" (:url v) "|" (or (:filename v) "file") ">")
                  (or (:filename v) "file"))
    (sequential? v) (str/join ", " (map upload->md v))
    :else (str v)))

(defn- radio-display
  "Selected option's label for a radio-group, appending the free-text detail
  when the applicant picked an 'other' option."
  [field answers]
  (let [chosen (get answers (:name field))
        option (some #(when (= chosen (:value %)) %) (:options field))]
    (when option
      (let [detail (:detail-field option)
            detail-value (and detail (get answers (:name detail)))]
        (if (present? detail-value)
          (str (:label option) " " detail-value)
          (:label option))))))

(defn- applicant-pairs [cfg answers]
  (for [field (config/main-fields cfg)
        :let [value (if (= :radio-group (:type field))
                      (radio-display field answers)
                      (get answers (:name field)))]
        :when (present? value)]
    [(:label field) value]))

(defn- sections-pair [cfg sections]
  (when (seq sections)
    (let [labels (into {} (map (juxt :value :label)) (:sections cfg))]
      ["Sections of interest" (str/join ", " (map #(get labels % %) sections))])))

(defn- supplemental-pairs [cfg supplementals]
  (for [field (config/supplemental-fields cfg)
        :let [file? (= :file (:type field))
              raw (get supplementals (:name field))
              value (if file? (upload->md raw) raw)]
        :when (if file? (some? raw) (present? value))]
    [(:label field) value]))

(defn- pair->line [[label value]]
  (let [s (str value)
        ;; Don't truncate values that contain a Slack link — it'd break syntax.
        shown (if (str/includes? s "<http") s (truncate s))]
    (str "*" label ":* " shown)))

(defn- pack-sections
  "Packs label/value lines into as few section blocks as fit under Slack's
  per-section character limit."
  [lines]
  (map (fn [text] {:type "section" :text {:type "mrkdwn" :text text}})
       (reduce (fn [chunks line]
                 (let [current (peek chunks)]
                   (if (and current
                            (<= (+ (count current) (count line) 1) max-section-chars))
                     (conj (pop chunks) (str current "\n" line))
                     (conj chunks line))))
               []
               lines)))

(defn- decision-buttons [value]
  {:type "actions"
   :block_id "decision"
   :elements [{:type "button" :action_id accept-action :style "primary"
               :text {:type "plain_text" :text "Accept"} :value value}
              {:type "button" :action_id deny-action :style "danger"
               :text {:type "plain_text" :text "Deny"} :value value}]})

(defn submission-payload
  "Builds the Slack Block Kit payload for a submitted application: a full,
  label-formatted breakdown of every answered field (labels from the config),
  followed by Accept/Deny buttons carrying the applicant identity."
  [{:keys [submission-id answers sections supplementals]}]
  (let [cfg config/membership-application
        name (applicant-name answers)
        button-value (json/write-str {:submissionId submission-id
                                      :name name
                                      :email (get answers "email")})
        pairs (concat (applicant-pairs cfg answers)
                      (when-let [p (sections-pair cfg sections)] [p])
                      (supplemental-pairs cfg supplementals))]
    {:text (str name " submitted an application")
     :blocks (concat
              [{:type "header"
                :text {:type "plain_text" :text "New application" :emoji true}}]
              (pack-sections (map pair->line pairs))
              [(decision-buttons button-value)])}))

;; ---------------------------------------------------------------------------
;; Request-signature verification
;; ---------------------------------------------------------------------------

(defn- hmac-sha256-hex [secret message]
  (let [mac (Mac/getInstance "HmacSHA256")]
    (.init mac (SecretKeySpec. (.getBytes ^String secret StandardCharsets/UTF_8)
                               "HmacSHA256"))
    (->> (.doFinal mac (.getBytes ^String message StandardCharsets/UTF_8))
         (map #(format "%02x" %))
         (apply str))))

(defn- recent? [timestamp]
  (try
    (< (Math/abs (- (quot (System/currentTimeMillis) 1000)
                    (Long/parseLong timestamp)))
       (* 60 5))
    (catch Exception _ false)))

(defn valid-request?
  "Verifies a Slack interaction request per Slack's signing scheme: HMAC-SHA256
  over `v0:{timestamp}:{raw-body}`, compared constant-time, with a 5-minute
  replay window."
  [signing-secret {:keys [headers slack-raw-body]}]
  (let [timestamp (get headers "x-slack-request-timestamp")
        signature (get headers "x-slack-signature")]
    (boolean
     (and signing-secret timestamp signature slack-raw-body
          (recent? timestamp)
          (let [expected (str "v0=" (hmac-sha256-hex
                                     signing-secret
                                     (str "v0:" timestamp ":" slack-raw-body)))]
            (MessageDigest/isEqual (.getBytes expected StandardCharsets/UTF_8)
                                   (.getBytes ^String signature StandardCharsets/UTF_8)))))))

;; ---------------------------------------------------------------------------
;; Slack Web API + interaction dispatch
;; ---------------------------------------------------------------------------

(defn- slack-post! [bot-token method payload]
  (let [request (-> (HttpRequest/newBuilder (URI/create (str "https://slack.com/api/" method)))
                    (.timeout (Duration/ofMillis 2500))
                    (.header "Content-Type" "application/json; charset=utf-8")
                    (.header "Authorization" (str "Bearer " bot-token))
                    (.POST (HttpRequest$BodyPublishers/ofString
                            (json/write-str payload) StandardCharsets/UTF_8))
                    .build)]
    (.send (HttpClient/newHttpClient) request (HttpResponse$BodyHandlers/ofString))))

(defn post-message!
  "Posts the submission message (with Accept/Deny buttons) to `channel` via the
  Web API `chat.postMessage`, using the bot token. Returns {:accepted? bool};
  never throws. chat.postMessage always returns HTTP 200, so success is read
  from the body's `ok` flag, not the status code."
  [bot-token channel application]
  (try
    (let [payload (assoc (submission-payload application) :channel channel)
          response (slack-post! bot-token "chat.postMessage" payload)
          body (json/read-str (.body response) :key-fn keyword)]
      (cond-> {:accepted? (boolean (:ok body))}
        (not (:ok body)) (assoc :error (:error body))))
    (catch Exception e
      {:accepted? false :error (str e)})))

(defn- review-modal
  "Accept opens a modal asking for the position name; Deny opens a lightweight
  confirmation with no input (a safety step against misclicks). An optional
  `error` renders a warning banner, used to re-show the modal after a send fails."
  ([decision meta] (review-modal decision meta nil))
  ([decision meta error]
   (let [accept? (= decision "accept")]
     {:type "modal"
      :callback_id modal-callback
      :private_metadata (json/write-str (assoc meta :decision decision))
      :title {:type "plain_text" :text (if accept? "Accept" "Deny")}
      :submit {:type "plain_text" :text (if accept? "Send email" "Send rejection")}
      :close {:type "plain_text" :text "Cancel"}
      :blocks
      (cond-> []
        error
        (conj {:type "section"
               :text {:type "mrkdwn" :text (str ":warning: " error)}})

        accept?
        (conj {:type "input"
               :block_id position-block
               :label {:type "plain_text" :text "Position name"}
               :element {:type "plain_text_input"
                         :action_id position-input
                         :placeholder {:type "plain_text" :text "e.g. News Writer"}}})

        (not accept?)
        (conj {:type "section"
               :text {:type "mrkdwn"
                      :text (str "Send a rejection email to *"
                                 (or (:name meta) "this applicant") "*?")}}))})))

(defn- ack [] {:status 200 :headers {} :body ""})

;; Bridges a button click to its modal submission: keyed by the message ts (which
;; both interactions share), it holds the channel + original blocks so the message
;; can be collapsed on a decision. Entries are removed once consumed.
(defonce ^:private pending-messages (atom {}))

(defn- handle-block-actions [bot-token payload]
  (let [action (first (:actions payload))
        decision (condp = (:action_id action)
                   accept-action "accept"
                   deny-action "deny"
                   nil)
        meta (json/read-str (:value action) :key-fn keyword)
        channel (get-in payload [:channel :id])
        ts (get-in payload [:message :ts])]
    (when decision
      (when ts
        (swap! pending-messages assoc ts
               {:channel channel :blocks (get-in payload [:message :blocks])}))
      (slack-post! bot-token "views.open"
                   {:trigger_id (:trigger_id payload)
                    :view (review-modal decision (assoc meta :channel channel :ts ts))}))
    (ack)))

(defn- decision-status-block [decision position]
  {:type "context"
   :elements [{:type "mrkdwn"
               :text (if (= "accept" decision)
                       (str ":white_check_mark: *Accepted*"
                            (when (present? position) (str " — " position)))
                       ":no_entry_sign: *Rejected*")}]})

(defn- collapse-message!
  "Replaces the original application message with a finished state: the same field
  breakdown minus the Accept/Deny buttons, plus a decision status line. Best-effort
  — a chat.update failure never affects the reviewer's already-completed action."
  [bot-token {:keys [channel ts name decision position]}]
  (when (and channel ts)
    (try
      (let [cached (get @pending-messages ts)
            kept (remove #(= "decision" (:block_id %)) (:blocks cached))
            base (if (seq kept)
                   kept
                   [{:type "section"
                     :text {:type "mrkdwn" :text (str "*" (or name "Application") "*")}}])
            blocks (vec (concat base [(decision-status-block decision position)]))]
        (slack-post! bot-token "chat.update"
                     {:channel channel :ts ts :blocks blocks
                      :text (str (or name "Application") " — "
                                 (if (= "accept" decision) "accepted" "rejected"))})
        (swap! pending-messages dissoc ts))
      (catch Exception e
        (println "[triapply] message collapse failed:" (str e))))))

(def ^:private send-failed-message "Could not send the email. Try again.")

(defn- json-response [body]
  {:status 200
   :headers {"Content-Type" "application/json"}
   :body (json/write-str body)})

(defn- send-failure-response
  "Accept can attach a field-scoped error to its position input; Deny has no
  input block, so it re-renders the confirmation modal with a warning banner."
  [decision-str meta]
  (if (= decision-str "accept")
    (json-response {:response_action "errors"
                    :errors {position-block send-failed-message}})
    (json-response {:response_action "update"
                    :view (review-modal decision-str meta send-failed-message)})))

(defn- handle-view-submission [bot-token send-email! record-decision! payload]
  (let [view (:view payload)
        meta (json/read-str (:private_metadata view) :key-fn keyword)
        decision-str (:decision meta)          ; "accept" / "deny"
        decision (keyword decision-str)
        position (get-in view [:state :values
                               (keyword position-block)
                               (keyword position-input)
                               :value])         ; nil for Deny (no input)
        {:keys [sent?]} (send-email! {:to (:email meta)
                                      :name (:name meta)
                                      :decision decision
                                      :position position})]
    (if sent?
      (do
        ;; Best-effort audit trail; never blocks or fails the reviewer's action.
        (record-decision! {:submission-id (:submissionId meta)
                           :decision decision
                           :position position})
        ;; Collapse the source message's buttons into a finished status.
        (collapse-message! bot-token (assoc meta :position position))
        (ack)) ;; Empty 200 closes the modal.
      (send-failure-response decision-str meta))))

(defn handle-interaction
  "Dispatches a verified Slack interaction. `payload-json` is the raw `payload`
  form field. `send-email!` takes {:to :name :decision :position} → {:sent? bool};
  `record-decision!` takes {:submission-id :decision :position} and is best-effort."
  [bot-token send-email! record-decision! payload-json]
  (let [payload (json/read-str payload-json :key-fn keyword)]
    (case (:type payload)
      "block_actions" (handle-block-actions bot-token payload)
      "view_submission" (handle-view-submission bot-token send-email! record-decision! payload)
      (ack))))
