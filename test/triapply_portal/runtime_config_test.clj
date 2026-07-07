(ns triapply-portal.runtime-config-test
  (:require
   [clojure.test :refer [deftest is testing]]
   [triapply-portal.runtime-config :as runtime-config]))

(deftest parses-deployment-port
  (is (= 8080 (runtime-config/parse-http-port "8080")))
  (testing "invalid ports fail during startup"
    (is (thrown? clojure.lang.ExceptionInfo
                 (runtime-config/parse-http-port "not-a-port")))
    (is (thrown? clojure.lang.ExceptionInfo
                 (runtime-config/parse-http-port "70000")))))
