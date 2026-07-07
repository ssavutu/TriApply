(ns triapply-portal.http-server-test
  (:require
   [clojure.string :as string]
   [clojure.test :refer [deftest is testing]]
   [triapply-portal.http-server :as http-server]))

(def valid-params
  {"name" "Jane Doe"
   "birthdate" "2000-01-01"
   "phone-number" "215-555-0100"
   "email" "jane@drexel.edu"
   "major" "Journalism"
   "grad-year" "2027"
   "coop-cycle" "Fall/Winter"
   "heard-from" "A friend"
   "other-clubs" "None"
   "prev-experience" "no"
   "section-interest" "distribution"
   "top-two" "Distribution, Copy Editing"})

(deftest explicit-routes
  (testing "the root page serves the application form"
    (let [response (http-server/handler {:request-method :get :uri "/"})]
      (is (= 200 (:status response)))
      (is (= "text/html; charset=utf-8"
             (get-in response [:headers "Content-Type"])))
      (is (string/starts-with? (:body response) "<!doctype html>"))
      (is (string/includes? (:body response) "href=\"/css/site.css\""))
      (is (string/includes? (:body response) "action=\"/applications\""))
      (is (string/includes? (:body response) "type=\"submit\""))
      (is (string/includes? (:body response) "src=\"/js/application-form.js\""))))

  (testing "unknown routes and unsupported methods are not successful"
    (is (= 404 (:status (http-server/handler {:request-method :get :uri "/missing"}))))
    (is (= 405 (:status (http-server/handler {:request-method :post :uri "/"}))))))

(deftest submission-boundary
  (testing "valid applications are not reported as delivered without an ATS"
    (let [response (http-server/handler {:request-method :post
                                         :uri "/applications"
                                         :params valid-params})]
      (is (= 503 (:status response)))
      (is (string/includes? (:body response) "was not submitted"))))

  (testing "an injected ATS submitter can accept an application"
    (let [handler (http-server/make-handler (constantly {:accepted? true}))
          response (handler {:request-method :post
                             :uri "/applications"
                             :params valid-params})]
      (is (= 303 (:status response)))
      (is (= "/applications/received" (get-in response [:headers "Location"]))))))
