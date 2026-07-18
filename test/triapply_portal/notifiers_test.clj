(ns triapply-portal.notifiers-test
  (:require
   [clojure.string :as str]
   [clojure.test :refer [deftest is testing]]
   [triapply-portal.notifiers :as notifiers]
   [triapply-portal.slack :as slack])
  (:import [java.io File]))

(defn- stored-upload [filename url]
  {:filename filename :content-type "application/pdf" :size 10
   :tempfile (File/createTempFile "upload" ".pdf") :url url})

(def application
  {:submission-id "abc-123"
   :answers {"name" "Jordan Lee"
             "email" "jl@drexel.edu"
             "resume" (stored-upload "resume.pdf" "https://files.triangle.org/abc-123/aa-resume.pdf")}
   :sections ["news-writing"]
   :supplementals {"portfolio-files" (stored-upload "port.pdf" "https://files.triangle.org/abc-123/bb-port.pdf")}})

(deftest upload-links-are-collected-for-the-sheet
  (let [links (notifiers/upload-links application)]
    (is (= #{"https://files.triangle.org/abc-123/aa-resume.pdf"
             "https://files.triangle.org/abc-123/bb-port.pdf"}
           (set (map :url links))))
    (is (= #{"resume.pdf" "port.pdf"} (set (map :filename links))))))

(deftest slack-message-renders-fields-and-file-links
  (let [app {:submission-id "abc-123"
             :answers {"name" "Jordan Lee" "email" "jl@drexel.edu"
                       "major" "Computer Science"}
             :sections ["it-team"]
             :supplementals {"resume" (stored-upload
                                       "resume.pdf"
                                       "https://files.triangle.org/abc-123/aa-resume.pdf")}}
        text (->> (:blocks (slack/submission-payload app))
                  (filter #(= "section" (:type %)))
                  (map #(get-in % [:text :text]))
                  (str/join "\n"))]
    (testing "fields render with their config labels in markdown"
      (is (str/includes? text "*Name:* Jordan Lee"))
      (is (str/includes? text "*Major:* Computer Science"))
      (is (str/includes? text "*Sections of interest:* IT Team")))
    (testing "the resume renders as a Slack link (per the Zapier-style layout)"
      (is (str/includes? text "https://files.triangle.org/abc-123/aa-resume.pdf")))))
