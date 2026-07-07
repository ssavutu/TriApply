(ns triapply-portal.main
  (:require
   [triapply-portal.http-server :as http-server]
   [triapply-portal.runtime-config :as runtime-config])
  (:gen-class))

(defn -main
  [& args]
  (let [port (runtime-config/http-port)]
    (http-server/start-server port)
    (println (str "TriApply Portal listening on http://localhost:" port))))
