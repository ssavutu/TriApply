(ns triapply-portal.mailer
  "Applicant accept/deny decision emails via the Gmail API.

  The message is built with jakarta.mail (correct MIME/encoding) but *sent* over
  HTTPS through the Gmail REST API, not SMTP: cloud hosts (e.g. DigitalOcean)
  commonly block outbound SMTP. Sending as the notify@ Google mailbox needs no
  domain DNS verification. The OAuth access token is cached so the common path is
  a single fast HTTPS call, staying inside Slack's ~3s modal-ack window."
  (:require
   [clojure.data.json :as json]
   [triapply-portal.runtime-config :as runtime-config])
  (:import
   [java.io ByteArrayOutputStream]
   [java.net URI URLEncoder]
   [java.net.http HttpClient HttpRequest HttpRequest$BodyPublishers
    HttpResponse$BodyHandlers]
   [java.nio.charset StandardCharsets]
   [java.time Duration]
   [java.util Base64 Properties]
   [jakarta.mail Message$RecipientType Session]
   [jakarta.mail.internet InternetAddress MimeMessage]))

(def ^:private call-timeout-millis 2500)
(def ^:private token-endpoint "https://oauth2.googleapis.com/token")
(def ^:private send-endpoint
  "https://gmail.googleapis.com/gmail/v1/users/me/messages/send")

(def ^:private http-client (HttpClient/newHttpClient))

;; Google access tokens live ~1h; caching one keeps the hot path to a single
;; Gmail API call. Refreshed 60s before expiry.
(def ^:private token-cache (atom {:token nil :expires-at 0}))

(defn- decision-content [{:keys [decision name position]}]
  (let [display (if (seq name) name "applicant")]
    (if (= :accept decision)
      {:subject (str "Your Triangle application: " position)
       :body (format
              (str "Hi %s,\n\n"
                   "Congratulations! You have been accepted to The Triangle as %s. "
                   "Onboarding information will follow shortly.\n\n"
                   "— The Triangle")
              display position)}
      {:subject "Your Triangle application"
       :body (format
              (str "Hi %s,\n\n"
                   "Thank you for applying to The Triangle. After careful review "
                   "we are unable to offer you a position at this time. We "
                   "encourage you to apply again in the future.\n\n"
                   "— The Triangle")
              display)})))

(defn- raw-message
  "Builds the RFC 5322 message and returns it base64url-encoded for the Gmail
  API `raw` field. jakarta.mail handles header encoded-words and the UTF-8 body
  transfer-encoding."
  [{:keys [from from-name]} to subject body]
  (let [msg (doto (MimeMessage. (Session/getInstance (Properties.)))
              (.setFrom (InternetAddress. from (or from-name from) "UTF-8"))
              (.setRecipient Message$RecipientType/TO (InternetAddress. to))
              (.setSubject subject "UTF-8")
              (.setText body "UTF-8"))
        baos (ByteArrayOutputStream.)]
    (.saveChanges msg)
    (.writeTo msg baos)
    (.encodeToString (Base64/getUrlEncoder) (.toByteArray baos))))

(defn- enc [v] (URLEncoder/encode (str v) StandardCharsets/UTF_8))

(defn- request-access-token
  [{:keys [client-id client-secret refresh-token]}]
  (let [form (str "client_id=" (enc client-id)
                  "&client_secret=" (enc client-secret)
                  "&refresh_token=" (enc refresh-token)
                  "&grant_type=refresh_token")
        request (-> (HttpRequest/newBuilder (URI/create token-endpoint))
                    (.timeout (Duration/ofMillis call-timeout-millis))
                    (.header "Content-Type" "application/x-www-form-urlencoded")
                    (.POST (HttpRequest$BodyPublishers/ofString
                            form StandardCharsets/UTF_8))
                    .build)
        response (.send http-client request (HttpResponse$BodyHandlers/ofString))
        status (.statusCode response)]
    (if (<= 200 status 299)
      (json/read-str (.body response) :key-fn keyword)
      (throw (ex-info (str "Google token endpoint returned " status)
                      {:status status :body (.body response)})))))

(defn- access-token [cfg]
  (let [{:keys [token expires-at]} @token-cache]
    (if (and token (< (System/currentTimeMillis) expires-at))
      token
      (let [{:keys [access_token expires_in]} (request-access-token cfg)
            ttl (* 1000 (- (or expires_in 3600) 60))]
        (reset! token-cache {:token access_token
                             :expires-at (+ (System/currentTimeMillis) ttl)})
        access_token))))

(defn- send-raw! [token raw]
  (let [request (-> (HttpRequest/newBuilder (URI/create send-endpoint))
                    (.timeout (Duration/ofMillis call-timeout-millis))
                    (.header "Authorization" (str "Bearer " token))
                    (.header "Content-Type" "application/json; charset=utf-8")
                    (.POST (HttpRequest$BodyPublishers/ofString
                            (json/write-str {:raw raw}) StandardCharsets/UTF_8))
                    .build)]
    (.send http-client request (HttpResponse$BodyHandlers/ofString))))

(defn send-decision-email!
  "Sends the accept/deny email via the Gmail API. `opts` is
  {:to email :name applicant-name :decision :accept|:deny :position str}.
  Returns {:sent? bool}; never throws."
  [{:keys [to] :as opts}]
  (try
    (if-let [cfg (runtime-config/mail-config)]
      (let [{:keys [subject body]} (decision-content opts)
            raw (raw-message cfg to subject body)
            response (send-raw! (access-token cfg) raw)
            status (.statusCode response)]
        (if (<= 200 status 299)
          {:sent? true}
          (do
            (println "[triapply] decision email to" to "failed:" status (.body response))
            {:sent? false :error (str "Gmail API returned " status)})))
      (do
        (println "[triapply] decision email skipped: Gmail API is not configured")
        {:sent? false :error "Email is not configured."}))
    (catch Exception e
      (println "[triapply] decision email to" to "failed:" (str e))
      {:sent? false :error (.getMessage e)})))
