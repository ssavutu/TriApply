(ns triapply.core-test
  (:require
   [clojure.string :as string]
   [clojure.test :refer [deftest is testing]]
   [triapply.web :as web]))

(deftest handler-renders-application-form
  (testing "the root page serves the application form"
    (let [response (web/handler {})]
      (is (= 200 (:status response)))
      (is (= "text/html" (get-in response [:headers "Content-Type"])))
      (is (string/includes? (:body response) "Apply to The Triangle")))))
