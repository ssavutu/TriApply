(ns triapply-portal.runtime-config)

(def default-http-port 4321)
(def default-ats-timeout-millis 10000)
(def default-notify-timeout-millis 10000)

(defn parse-http-port [value]
  (let [port (parse-long value)]
    (cond
      (nil? port)
      (throw (ex-info "PORT must be an integer." {:value value}))

      (<= 1 port 65535)
      port

      :else
      (throw (ex-info "PORT must be between 1 and 65535." {:value value})))))

(defn http-port []
  (if-let [value (System/getenv "PORT")]
    (parse-http-port value)
    default-http-port))

(defn parse-positive-int [name value]
  (let [parsed (parse-long value)]
    (if (and parsed (pos? parsed))
      parsed
      (throw (ex-info (str name " must be a positive integer.")
                      {:value value})))))

(defn ats-url []
  (System/getenv "TRIAPPLY_ATS_URL"))

(defn ats-timeout-millis []
  (if-let [value (System/getenv "TRIAPPLY_ATS_TIMEOUT_MILLIS")]
    (parse-positive-int "TRIAPPLY_ATS_TIMEOUT_MILLIS" value)
    default-ats-timeout-millis))

(defn slack-channel []
  (System/getenv "TRIAPPLY_SLACK_CHANNEL"))

(defn upload-dir []
  (System/getenv "TRIAPPLY_UPLOAD_DIR"))

(defn file-base-url []
  (or (System/getenv "TRIAPPLY_FILE_BASE_URL") "https://files.triangle.org"))

(defn slack-bot-token []
  (System/getenv "TRIAPPLY_SLACK_BOT_TOKEN"))

(defn slack-signing-secret []
  (System/getenv "TRIAPPLY_SLACK_SIGNING_SECRET"))

(defn mail-config
  "Gmail-API (OAuth2) email config, or nil unless the OAuth client id/secret and
  a refresh token are all set. Mail is sent as the `from` Google mailbox via the
  Gmail REST API over HTTPS, so no domain DNS verification is needed and the
  host's outbound-SMTP block doesn't apply. `from` must be the mailbox the
  refresh token was issued for (or one it can send-as)."
  []
  (let [client-id (System/getenv "TRIAPPLY_GMAIL_CLIENT_ID")
        client-secret (System/getenv "TRIAPPLY_GMAIL_CLIENT_SECRET")
        refresh-token (System/getenv "TRIAPPLY_GMAIL_REFRESH_TOKEN")]
    (when (and client-id client-secret refresh-token)
      {:client-id client-id
       :client-secret client-secret
       :refresh-token refresh-token
       :from (or (System/getenv "TRIAPPLY_MAIL_FROM") "notify@thetriangle.org")
       :from-name (or (System/getenv "TRIAPPLY_MAIL_FROM_NAME") "The Triangle")})))

(defn sheet-webhook-url []
  (System/getenv "TRIAPPLY_SHEET_WEBHOOK_URL"))

(defn notify-timeout-millis []
  (if-let [value (System/getenv "TRIAPPLY_NOTIFY_TIMEOUT_MILLIS")]
    (parse-positive-int "TRIAPPLY_NOTIFY_TIMEOUT_MILLIS" value)
    default-notify-timeout-millis))
