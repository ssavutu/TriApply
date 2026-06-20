(ns triapply.web
  (:require
   [ring.adapter.jetty :refer [run-jetty]]
   [ring.middleware.content-type :refer [wrap-content-type]]
   [ring.middleware.reload :refer [wrap-reload]]
   [ring.middleware.resource :refer [wrap-resource]]
   [triapply.views.apply :as apply]))

(defn handler [_]
  {:status 200
   :headers {"Content-Type" "text/html"}
   :body (apply/apply-page)})

(def app
  (-> #'handler
      (wrap-resource "public")
      wrap-content-type
      wrap-reload))

(defonce server
  (atom nil))

(defn stop-server []
  (when-let [s @server]
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
