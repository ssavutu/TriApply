(ns triapply-portal.storage-test
  (:require
   [clojure.java.io :as io]
   [clojure.string :as str]
   [clojure.test :refer [deftest is testing]]
   [triapply-portal.runtime-config :as runtime-config]
   [triapply-portal.storage :as storage])
  (:import
   [java.io File]
   [java.nio.file Files]
   [java.nio.file.attribute FileAttribute]))

(defn- temp-dir []
  (str (Files/createTempDirectory "triapply-storage-test"
                                  (make-array FileAttribute 0))))

(defn- upload [filename content]
  (let [f (File/createTempFile "upload" ".pdf")]
    (spit f content)
    {:filename filename :content-type "application/pdf" :size (count content)
     :tempfile f}))

(deftest persist-writes-file-and-stamps-url
  (let [dir (temp-dir)]
    (with-redefs [runtime-config/upload-dir (constantly dir)
                  runtime-config/file-base-url (constantly "https://files.triangle.org/")]
      (let [app {:submission-id "abc-123"
                 :answers {"name" "Jordan"
                           "resume" (upload "My Resume.pdf" "%PDF-1.4 body")}
                 :sections ["news-writing"]
                 :supplementals {}}
            result (storage/persist-uploads app)
            url (get-in result [:answers "resume" :url])]
        (testing "a public URL is stamped onto the upload"
          (is (string? url))
          (is (str/starts-with? url "https://files.triangle.org/abc-123/"))
          (is (str/ends-with? url "-My_Resume.pdf")))
        (testing ":tempfile is preserved so the ATS sink can still stream bytes"
          (is (instance? File (get-in result [:answers "resume" :tempfile]))))
        (testing "the bytes are written to disk under the base dir"
          (let [rel (str/replace url "https://files.triangle.org/" "")]
            (is (= "%PDF-1.4 body" (slurp (io/file dir rel))))))))))

(deftest persist-is-noop-without-config
  (with-redefs [runtime-config/upload-dir (constantly nil)]
    (let [app {:submission-id "x"
               :answers {"resume" (upload "r.pdf" "x")}
               :sections [] :supplementals {}}]
      (is (nil? (get-in (storage/persist-uploads app) [:answers "resume" :url]))))))

(deftest submission-id-cannot-escape-base-dir
  (let [dir (temp-dir)]
    (with-redefs [runtime-config/upload-dir (constantly dir)
                  runtime-config/file-base-url (constantly "https://files.triangle.org")]
      (let [app {:submission-id "../../etc"
                 :answers {"resume" (upload "passwd" "data")}
                 :sections [] :supplementals {}}
            url (get-in (storage/persist-uploads app) [:answers "resume" :url])]
        (testing "path separators in submissionId are neutralized (no traversal)"
          (is (not (str/includes? url "/../")))
          (is (str/starts-with? url "https://files.triangle.org/.._.._etc/")))
        (testing "the file lands inside the configured base dir"
          (let [rel (str/replace url "https://files.triangle.org/" "")
                written (io/file dir rel)]
            (is (.exists written))
            (is (str/starts-with? (.getCanonicalPath written)
                                  (.getCanonicalPath (io/file dir))))))))))
