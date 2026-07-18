(ns triapply-portal.http-server
  (:require
   [clojure.string :as str]
   [reitit.ring :as ring]
   [ring.adapter.jetty :refer [run-jetty]]
   [ring.middleware.content-type :refer [wrap-content-type]]
   [ring.middleware.multipart-params :refer [wrap-multipart-params]]
   [ring.middleware.params :refer [wrap-params]]
   [triapply-portal.ats-client :as ats-client]
   [triapply-portal.application-submission :as submission]
   [triapply-portal.mailer :as mailer]
   [triapply-portal.notifiers :as notifiers]
   [triapply-portal.runtime-config :as runtime-config]
   [triapply-portal.slack :as slack]
   [triapply-portal.storage :as storage]
   [triapply-portal.views.application-form :as application-form]
   [triapply-portal.views.layout :as layout]
   [hiccup2.core :as h])
  (:import
   [java.io ByteArrayInputStream]
   [java.nio.charset StandardCharsets]
   [org.eclipse.jetty.server Server]))

(def max-upload-size (* 10 1024 1024))

(defn- html-response [status body]
  {:status status
   :headers {"Content-Type" "text/html; charset=utf-8"}
   :body body})

(defn- status-main [title message]
  [:main {:class "mx-auto mt-16 max-w-2xl rounded-xl border border-triangleGray bg-white p-8 font-roboto-slab"}
   [:h1 {:class "font-playfair text-3xl font-bold text-triangleDark"} title]
   [:p {:class "mt-4 text-triangleDark/80"} message]
   [:a {:class "mt-6 inline-block font-bold text-triangleBlue hover:underline"
        :href "/"}
    "Return to the application"]])

(defn- status-page [title message]
  (layout/page title (status-main title message)))

;; Key must match the one the client autosave uses in
;; src-cljs/.../client/application_form.cljs.
(def ^:private draft-storage-key "triapply-application-v1")

(defn- received-page []
  (layout/page
   "Application received"
   (status-main "Application received" "Your application was received successfully.")
   ;; The submission succeeded, so discard the saved draft.
   [:script (h/raw (str "try{window.localStorage.removeItem('"
                        draft-storage-key "')}catch(e){}"))]))

(defn- error-page [errors]
  (layout/page
   "Application needs attention"
   [:main {:class "mx-auto mt-16 max-w-2xl rounded-xl border border-triangleDanger bg-white p-8 font-roboto-slab"}
    [:h1 {:class "font-playfair text-3xl font-bold text-triangleDark"}
     "Application needs attention"]
    [:p {:class "mt-4 text-triangleDark/80"}
     "Correct the following fields and submit again:"]
    (into [:ul {:class "mt-4 list-disc space-y-2 pl-6 text-triangleDanger"}]
          (map (fn [[_ message]] [:li message]) (sort-by key errors)))
    [:a {:class "mt-6 inline-block font-bold text-triangleBlue hover:underline"
         :href "/"}
     "Return to the application"]]))

(defn- home-handler [_]
  (html-response 200 (application-form/application-page)))

(defn- submission-id [params]
  (let [provided (get params "submission-id")]
    (if (and (string? provided) (not (str/blank? provided)))
      provided
      (str (random-uuid)))))

(defn- create-application-handler [deliver!]
  (fn [{:keys [params]}]
    (let [errors (submission/validate params)]
      (if (seq errors)
        (html-response 422 (error-page errors))
        (let [application (-> (submission/normalized params)
                              (assoc :submission-id (submission-id params))
                              storage/persist-uploads)]
          (if-let [result (deliver! application)]
            (if (:accepted? result)
              {:status 303
               :headers {"Location" "/applications/received"}
               :body ""}
              (html-response
               503
               (status-page "Submission unavailable"
                            "The application service is temporarily unavailable. Your application was not submitted.")))
            (html-response
             503
             (status-page "Submission unavailable"
                          "No delivery destination has been configured. Your application was not submitted."))))))))

(defn- received-handler [_]
  (html-response 200 (received-page)))

(defn- sink
  "Wraps a delivery call so it never throws. Logs both thrown failures (including
  Errors such as a missing class) and clean rejections (a non-2xx / not-ok
  response), with the sink's name and any detail (e.g. HTTP status), so a failed
  delivery always names its culprit. Returns true only when the destination
  accepted."
  [label f]
  (fn [application]
    (let [result (try
                   (f application)
                   (catch Throwable t
                     (println "[triapply] sink" label "threw:" (str t))
                     {:accepted? false}))]
      (when-not (:accepted? result)
        (println "[triapply] sink" label "rejected:"
                 (pr-str (dissoc result :accepted?))))
      (boolean (:accepted? result)))))

(defn- configured-sinks
  "Builds the list of independent required sinks from runtime configuration.
  Only destinations with configuration present participate."
  []
  (cond-> []
    (runtime-config/ats-url)
    (conj (sink "ats" #(ats-client/submit-application!
                        (runtime-config/ats-url)
                        (runtime-config/ats-timeout-millis)
                        %)))

    (and (runtime-config/slack-bot-token) (runtime-config/slack-channel))
    (conj (sink "slack" #(slack/post-message!
                          (runtime-config/slack-bot-token)
                          (runtime-config/slack-channel)
                          %)))

    (runtime-config/sheet-webhook-url)
    (conj (sink "sheet" #(notifiers/append-sheet!
                          (runtime-config/sheet-webhook-url)
                          (runtime-config/notify-timeout-millis)
                          %)))))

(defn- configured-deliverer []
  (let [sinks (configured-sinks)]
    (when (seq sinks)
      (fn [application]
        ;; Force every sink to run (mapv, not lazy) so each destination
        ;; receives the application; the redirect only happens when all accept.
        {:accepted? (every? true? (mapv #(% application) sinks))}))))

(defn- decision-recorder
  "Best-effort callback that writes a decision back to the Google Sheet row,
  or a no-op when no Sheet webhook is configured. Runs asynchronously: the Sheet
  write (a slow Apps Script round-trip) must not delay the reply to Slack, which
  has a ~3s ack window on a modal submission. Audit-only, so failures are logged
  and swallowed."
  []
  (if-let [url (runtime-config/sheet-webhook-url)]
    (fn [decision]
      (future
        (try
          (notifiers/record-decision! url (runtime-config/notify-timeout-millis) decision)
          (catch Throwable t
            (println "[triapply] decision record failed:" (str t)))))
      nil)
    (constantly nil)))

(defn- slack-interactions-handler [request]
  (let [signing-secret (runtime-config/slack-signing-secret)
        bot-token (runtime-config/slack-bot-token)]
    (cond
      (or (nil? signing-secret) (nil? bot-token))
      {:status 503 :headers {} :body "Slack interactivity is not configured."}

      (not (slack/valid-request? signing-secret request))
      {:status 401 :headers {} :body ""}

      :else
      (slack/handle-interaction bot-token
                                mailer/send-decision-email!
                                (decision-recorder)
                                (get-in request [:params "payload"])))))

(defn make-handler
  ([] (make-handler (or (configured-deliverer) (constantly nil))))
  ([deliver!]
   (ring/ring-handler
    (ring/router
     [["/" {:get home-handler}]
      ["/applications" {:post (create-application-handler deliver!)}]
      ["/applications/received" {:get received-handler}]
      ["/slack/interactions" {:post slack-interactions-handler}]])
    (ring/routes
     (ring/create-resource-handler {:path "/"})
     (ring/create-default-handler
      {:not-found (fn [_]
                    (html-response 404 (status-page "Not found" "That page does not exist.")))
       :method-not-allowed (fn [_]
                             (html-response 405 (status-page "Method not allowed" "That request method is not supported.")))})))))

(def handler
  (make-handler))

(defn- wrap-error-logging
  "Logging is NOP by default (no SLF4J provider), so an uncaught Throwable in a
  handler would otherwise close the connection with no response and no log —
  surfacing only as an nginx 502. This catches everything, prints the stack
  trace to stdout (captured by journald), and returns a real 500 page."
  [handler]
  (fn [request]
    (try
      (handler request)
      (catch Throwable t
        (println "[triapply] unhandled error on" (:request-method request)
                 (:uri request) "->" (str t))
        (.printStackTrace t)
        (html-response 500 (status-page "Server error"
                                        "An unexpected error occurred."))))))

(defn- wrap-slack-raw-body
  "Captures the raw request body for the Slack interactions route before
  wrap-params consumes it (Slack signatures are computed over the raw bytes),
  then restores the body stream so form-param parsing still works."
  [handler]
  (fn [request]
    (if (and (= :post (:request-method request))
             (= "/slack/interactions" (:uri request))
             (:body request))
      (let [raw (slurp (:body request))]
        (handler (assoc request
                        :slack-raw-body raw
                        :body (ByteArrayInputStream.
                               (.getBytes raw StandardCharsets/UTF_8)))))
      (handler request))))

(def app
  (-> #'handler
      wrap-error-logging
      (wrap-multipart-params {:max-file-size max-upload-size
                              :max-file-count 50})
      wrap-params
      wrap-content-type
      wrap-slack-raw-body))

(defonce server
  (atom nil))

(defn stop-server []
  (when-let [^Server s @server]
    (.stop s)
    (reset! server nil)))

(defn start-server
  ([] (start-server 4321))
  ([port]
   (reset! server
           (run-jetty #'app
                      {:port port
                       :join? false}))))

(defn restart-server
  ([] (restart-server 4321))
  ([port]
   (stop-server)
   (start-server port)))
