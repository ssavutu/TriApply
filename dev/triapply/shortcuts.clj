(ns triapply.shortcuts
  (:require [triapply.web :as web]))

(defn start []
  (web/start-server))

(defn stop []
  (web/stop-server))

(defn restart []
  (web/restart-server))

(defn start-alt []
  (web/start-server 4322))

(defn restart-alt []
  (web/restart-server 4322))

(comment
  ;; Put the cursor after a form and run Calva: Evaluate Current Form.

  (start)
  (stop)
  (restart)

  ;; Alternate port for when 4321 is already occupied.
  (start-alt)
  (restart-alt)

  ;; Quick handler check without a browser.
  (web/handler nil))
