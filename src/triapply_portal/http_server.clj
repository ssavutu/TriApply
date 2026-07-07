(ns triapply-portal.http-server
  (:require
   [reitit.ring :as ring]
   [ring.adapter.jetty :refer [run-jetty]]
   [ring.middleware.content-type :refer [wrap-content-type]]
   [ring.middleware.multipart-params :refer [wrap-multipart-params]]
   [ring.middleware.params :refer [wrap-params]]
   [triapply-portal.ats-client :as ats-client]
   [triapply-portal.application-submission :as submission]
   [triapply-portal.runtime-config :as runtime-config]
   [triapply-portal.views.application-form :as application-form]
   [triapply-portal.views.layout :as layout])
  (:import [org.eclipse.jetty.server Server]))

(def max-upload-size (* 10 1024 1024))

(defn- html-response [status body]
  {:status status
   :headers {"Content-Type" "text/html; charset=utf-8"}
   :body body})

(defn- status-page [title message]
  (layout/page
   title
   [:main {:class "mx-auto mt-16 max-w-2xl rounded-xl border border-triangleGray bg-white p-8 font-roboto-slab"}
    [:h1 {:class "font-playfair text-3xl font-bold text-triangleDark"} title]
    [:p {:class "mt-4 text-triangleDark/80"} message]
    [:a {:class "mt-6 inline-block font-bold text-triangleBlue hover:underline"
         :href "/"}
     "Return to the application"]]))

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

(defn- create-application-handler [submit-application!]
  (fn [{:keys [params]}]
    (let [errors (submission/validate params)]
      (if (seq errors)
        (html-response 422 (error-page errors))
        (if-let [result (submit-application! (submission/normalized params))]
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
                        "ATS delivery has not been configured. Your application was not submitted.")))))))

(defn- received-handler [_]
  (html-response
   200
   (status-page "Application received"
                "Your application was received successfully.")))

(defn- configured-submitter []
  (when-let [ats-url (runtime-config/ats-url)]
    (let [timeout-millis (runtime-config/ats-timeout-millis)]
      (fn [application]
        (try
          (ats-client/submit-application! ats-url timeout-millis application)
          (catch Exception _
            {:accepted? false}))))))

(defn make-handler
  ([] (make-handler (or (configured-submitter) (constantly nil))))
  ([submit-application!]
   (ring/ring-handler
    (ring/router
     [["/" {:get home-handler}]
      ["/applications" {:post (create-application-handler submit-application!)}]
      ["/applications/received" {:get received-handler}]])
    (ring/routes
     (ring/create-resource-handler {:path "/"})
     (ring/create-default-handler
      {:not-found (fn [_]
                    (html-response 404 (status-page "Not found" "That page does not exist.")))
       :method-not-allowed (fn [_]
                             (html-response 405 (status-page "Method not allowed" "That request method is not supported.")))})))))

(def handler
  (make-handler))

(def app
  (-> #'handler
      (wrap-multipart-params {:max-file-size max-upload-size
                              :max-file-count 50})
      wrap-params
      wrap-content-type))

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
