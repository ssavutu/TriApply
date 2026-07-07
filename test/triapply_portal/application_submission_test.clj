(ns triapply-portal.application-submission-test
  (:require
   [clojure.test :refer [deftest is testing]]
   [triapply-portal.application-rules :as rules]
   [triapply-portal.application-submission :as submission])
  (:import [java.io File]))

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

(deftest section-selection-rules
  (testing "the shared rule enforces both bounds"
    (is (false? (:valid? (rules/selection-state [] 5))))
    (is (:valid? (rules/selection-state ["news-writing"] 5)))
    (is (false? (:valid? (rules/selection-state
                          ["news-writing" "opinion-writing" "sports-writing"
                           "photography" "copy-editing" "distribution"]
                          5)))))

  (testing "the server rejects too many sections"
    (let [params (assoc valid-params "section-interest"
                        ["news-writing" "opinion-writing" "sports-writing"
                         "photography" "copy-editing" "distribution"])]
      (is (contains? (submission/validate params) "section-interest")))))

(deftest malformed-values-are-rejected
  (testing "repeated scalar fields cannot crash or bypass validation"
    (let [errors (submission/validate
                  (assoc valid-params "email"
                         ["jane@drexel.edu" "attacker@example.com"]))]
      (is (contains? errors "email")))))

(deftest conditional-requirements
  (testing "writing applicants need a writing sample"
    (let [errors (submission/validate
                  (assoc valid-params "section-interest" "news-writing"))]
      (is (contains? errors "writing-samples"))))

  (testing "uploaded PDFs are checked by type, extension, and signature"
    (let [file (File/createTempFile "triapply-test-" ".pdf")]
      (try
        (spit file "%PDF-1.7 test")
        (let [upload {:filename "sample.pdf"
                      :content-type "application/pdf"
                      :tempfile file
                      :size (.length file)}
              params (assoc valid-params
                            "section-interest" "news-writing"
                            "writing-samples" upload)]
          (is (empty? (submission/validate params)))
          (is (contains? (submission/validate
                          (assoc-in params ["writing-samples" :content-type]
                                    "text/plain"))
                         "writing-samples")))
        (finally
          (.delete file)))))

  (testing "a portfolio URL satisfies the portfolio file-or-link rule"
    (let [params (assoc valid-params
                        "section-interest" "photography"
                        "portfolio-link" "https://portfolio.example.com")]
      (is (empty? (submission/validate params)))))

  (testing "portfolio applicants must supply a file or link"
    (let [errors (submission/validate
                  (assoc valid-params "section-interest" "photography"))]
      (is (contains? errors "portfolio-files"))))

  (testing "choosing other experience requires an explanation"
    (let [errors (submission/validate
                  (assoc valid-params "prev-experience" "other"))]
      (is (contains? errors "prev-experience-other")))))
