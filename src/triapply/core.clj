(ns triapply.core
  (:require [triapply.web :as web])
  (:gen-class))

(defn -main
  [& args]
  (web/start-server)
  (println "TriApply listening on http://localhost:4321"))
