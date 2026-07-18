(ns triapply-portal.notifiers
  "Best-effort side sinks (Slack, Google Sheets) for submitted applications.

  Each destination is treated as an independent required sink: it returns
  {:accepted? bool}, and the caller only redirects the applicant once every
  configured sink has accepted. Retries are made safe by the per-submission
  :submission-id, which the Sheets Apps Script uses to de-duplicate rows and
  which Slack messages carry for manual reconciliation."
  (:require
   [clojure.data.json :as json])
  (:import
   [java.io File]
   [java.net URI]
   [java.net.http HttpClient HttpClient$Redirect HttpRequest
    HttpRequest$BodyPublishers HttpResponse$BodyHandlers]
   [java.nio.charset StandardCharsets]
   [java.time Duration]))

;; Follows redirects: Apps Script /exec runs the script and then responds with a
;; 302 to googleusercontent.com. Without following it we'd see the 302 (not 2xx)
;; and wrongly report the write as failed — even though the row was written.
(def ^:private http-client
  (-> (HttpClient/newBuilder)
      (.followRedirects HttpClient$Redirect/NORMAL)
      (.build)))

(defn- upload? [value]
  (and (map? value) (instance? File (:tempfile value))))

(defn- sanitize
  "Replaces uploaded-file maps with lightweight metadata so applications can be
  serialized to JSON without dragging file bytes into the payload."
  [value]
  (cond
    (upload? value)
    (cond-> {:filename (:filename value)
             :content-type (:content-type value)
             :size (:size value)}
      (:url value) (assoc :url (:url value)))

    (map? value)
    (into {} (map (fn [[k v]] [k (sanitize v)])) value)

    (sequential? value)
    (mapv sanitize value)

    :else value))

(defn- post-json!
  "POSTs `payload` as JSON to `url`, returning {:accepted? bool}. Never throws;
  transport and non-2xx responses resolve to {:accepted? false}."
  [url timeout-millis payload]
  (try
    (let [body (json/write-str payload)
          request (-> (HttpRequest/newBuilder (URI/create url))
                      (.timeout (Duration/ofMillis timeout-millis))
                      (.header "Content-Type" "application/json; charset=utf-8")
                      (.POST (HttpRequest$BodyPublishers/ofString
                              body StandardCharsets/UTF_8))
                      .build)
          response (.send http-client
                          request
                          (HttpResponse$BodyHandlers/ofString))]
      {:accepted? (<= 200 (.statusCode response) 299)
       :status (.statusCode response)})
    (catch Exception _
      {:accepted? false})))

(defn- collect-uploads [value]
  (cond
    (upload? value) [value]
    (map? value) (mapcat collect-uploads (vals value))
    (sequential? value) (mapcat collect-uploads value)
    :else []))

(defn upload-links
  "Flat list of {:filename :url} for every stored upload in the application, so
  the Sheet can show clickable file links in a dedicated column. Uploads not yet
  persisted (no :url) are skipped. Intentionally Sheet-only — links are never
  sent to Slack, per reviewer preference."
  [{:keys [answers supplementals]}]
  (->> (collect-uploads {:a answers :s supplementals})
       (keep (fn [u] (when (:url u) {:filename (:filename u) :url (:url u)})))
       vec))

(defn append-sheet!
  "Sends the full application to the Sheets Apps Script webhook for appending.
  The script de-duplicates on submissionId, so re-sends are idempotent."
  [webhook-url timeout-millis {:keys [submission-id answers sections supplementals]
                               :as application}]
  (post-json! webhook-url timeout-millis
              {:type "submission"
               :submissionId submission-id
               :answers (sanitize answers)
               :sections (vec sections)
               :supplementals (sanitize supplementals)
               :files (upload-links application)}))

(defn record-decision!
  "Writes the accept/deny decision and position back onto the application's Sheet
  row (matched by submissionId), for an audit trail. Best-effort."
  [webhook-url timeout-millis {:keys [submission-id decision position]}]
  (post-json! webhook-url timeout-millis
              {:type "decision"
               :submissionId submission-id
               :decision (name decision)
               :position position}))
