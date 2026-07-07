(ns triapply-portal.shortcuts
  (:require [triapply-portal.http-server :as http-server]))

(defn start []
  (http-server/start-server))

(defn stop []
  (http-server/stop-server))

(defn restart []
  (http-server/restart-server))

(defn start-alt []
  (http-server/start-server 4322))

(defn restart-alt []
  (http-server/restart-server 4322))

(comment
  ;; Put the cursor after a form and run Calva: Evaluate Current Form.

  (start)
  (stop)
  (restart)

  ;; Alternate port for when 4321 is already occupied.
  (start-alt)
  (restart-alt)

  ;; Quick handler check without a browser.
  (http-server/handler {:request-method :get :uri "/"}))
