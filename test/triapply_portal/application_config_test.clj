(ns triapply-portal.application-config-test
  (:require
   [clojure.test :refer [deftest is testing]]
   [triapply-portal.application-config :as config]
   [triapply-portal.application-submission :as submission]
   [triapply-portal.views.application-form :as application-form]))

(deftest reads-versioned-form-dsl
  (let [config (config/read-config)]
    (is (= 1 (:version config)))
    (is (= "Triangle Spring 2026 Application"
           (get-in config [:page :application-title])))
    (is (= "name" (get-in config [:applicant :fields 0 :name])))
    (is (= 12 (count (:sections config))))))

(deftest rejects-invalid-dsl
  (testing "section values must be unique"
    (let [invalid-config (update config/membership-application :sections conj
                                 (first (:sections config/membership-application)))]
      (is (thrown-with-msg? clojure.lang.ExceptionInfo
                            #"Section values must be unique"
                            (config/validate-config invalid-config)))))

  (testing "unknown content block types fail during configuration loading"
    (let [invalid-config (assoc config/membership-application
                                :introduction [{:type :video}])]
      (is (thrown-with-msg? clojure.lang.ExceptionInfo
                            #"unsupported content block"
                            (config/validate-config invalid-config))))))

(deftest content-and-validation-come-from-config
  (testing "page copy is rendered from the supplied DSL"
    (let [form-config (assoc-in config/membership-application
                                [:page :application-title]
                                "Config-driven application")]
      (is (.contains (application-form/application-page form-config)
                     "Config-driven application"))))

  (testing "validation messages are read from field configuration"
    (let [form-config (assoc-in config/membership-application
                                [:applicant :fields 4 :validation :message]
                                "Use the configured email domain.")
          errors (submission/validate form-config {"email" "person@example.com"})]
      (is (= "Use the configured email domain." (get errors "email"))))))
