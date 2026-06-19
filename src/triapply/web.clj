(ns triapply.web
  (:require
   [ring.adapter.jetty :refer [run-jetty]]
   [triapply.views.apply :as apply]))

(defn handler [_]
  {:status 200
   :headers {"Content-Type" "text/html"}
   :body (apply/apply-page)})

(defn start-server []
  (run-jetty handler
             {:port 4321
              :join? false}))
