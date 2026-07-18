(ns triapply-portal.slack-test
  (:require
   [clojure.data.json :as json]
   [clojure.string :as str]
   [clojure.test :refer [deftest is testing]]
   [triapply-portal.slack :as slack])
  (:import
   [java.nio.charset StandardCharsets]
   [javax.crypto Mac]
   [javax.crypto.spec SecretKeySpec]))

(def secret "test-signing-secret")

(defn- sign [timestamp body]
  (let [mac (doto (Mac/getInstance "HmacSHA256")
              (.init (SecretKeySpec. (.getBytes secret StandardCharsets/UTF_8)
                                     "HmacSHA256")))
        digest (.doFinal mac (.getBytes (str "v0:" timestamp ":" body)
                                        StandardCharsets/UTF_8))]
    (str "v0=" (apply str (map #(format "%02x" %) digest)))))

(defn- request [timestamp body signature]
  {:slack-raw-body body
   :headers {"x-slack-request-timestamp" timestamp
             "x-slack-signature" signature}})

(deftest signature-verification
  (let [now (str (quot (System/currentTimeMillis) 1000))
        body "payload=%7B%7D"]
    (testing "a correctly signed, recent request is accepted"
      (is (slack/valid-request? secret (request now body (sign now body)))))

    (testing "a tampered body is rejected"
      (is (not (slack/valid-request? secret (request now "payload=evil" (sign now body))))))

    (testing "a stale timestamp is rejected (replay protection)"
      (let [old (str (- (Long/parseLong now) 3600))]
        (is (not (slack/valid-request? secret (request old body (sign old body)))))))

    (testing "a missing signature is rejected"
      (is (not (slack/valid-request? secret (request now body nil)))))))

(deftest submission-payload-has-decision-buttons
  (let [payload (slack/submission-payload
                 {:submission-id "abc-123"
                  :answers {"name" "Jordan Lee" "email" "jl@drexel.edu"}
                  :sections ["news-writing"]})
        actions (->> (:blocks payload) (filter #(= "actions" (:type %))) first)
        ids (set (map :action_id (:elements actions)))]
    (is (contains? ids slack/accept-action))
    (is (contains? ids slack/deny-action))
    (testing "buttons carry applicant identity for the interaction handler"
      (let [value (json/read-str (:value (first (:elements actions))) :key-fn keyword)]
        (is (= "jl@drexel.edu" (:email value)))
        (is (= "abc-123" (:submissionId value)))))))

(deftest view-submission-sends-decision-email-and-records-it
  (let [sent (atom nil)
        recorded (atom nil)
        send-email! (fn [opts] (reset! sent opts) {:sent? true})
        record! (fn [opts] (reset! recorded opts))
        payload (json/write-str
                 {:type "view_submission"
                  :view {:private_metadata (json/write-str
                                            {:decision "accept"
                                             :submissionId "abc-123"
                                             :name "Jordan Lee"
                                             :email "jl@drexel.edu"})
                         :state {:values {:position-block
                                          {:position {:value "News Writer"}}}}}})
        response (slack/handle-interaction "xoxb-token" send-email! record! payload)]
    (is (= 200 (:status response)))
    (is (= {:to "jl@drexel.edu"
            :name "Jordan Lee"
            :decision :accept
            :position "News Writer"}
           @sent))
    (testing "the decision is written back for the audit trail"
      (is (= {:submission-id "abc-123"
              :decision :accept
              :position "News Writer"}
             @recorded)))))

(deftest deny-submission-sends-rejection-without-a-position
  (let [sent (atom nil)
        recorded (atom nil)
        send-email! (fn [opts] (reset! sent opts) {:sent? true})
        record! (fn [opts] (reset! recorded opts))
        payload (json/write-str
                 {:type "view_submission"
                  :view {:private_metadata (json/write-str
                                            {:decision "deny"
                                             :submissionId "z-9"
                                             :name "Sam Rivers"
                                             :email "sr@drexel.edu"})
                         ;; Deny modal has no position input, so state is empty.
                         :state {:values {}}}})
        response (slack/handle-interaction "xoxb-token" send-email! record! payload)]
    (is (= 200 (:status response)))
    (is (= {:to "sr@drexel.edu" :name "Sam Rivers" :decision :deny :position nil}
           @sent))
    (is (= {:submission-id "z-9" :decision :deny :position nil} @recorded))))

(deftest accept-email-failure-shows-field-error-without-recording
  (let [recorded (atom nil)
        send-email! (constantly {:sent? false :error "smtp down"})
        record! (fn [opts] (reset! recorded opts))
        payload (json/write-str
                 {:type "view_submission"
                  :view {:private_metadata (json/write-str {:decision "accept"
                                                            :submissionId "z-9"
                                                            :email "x@drexel.edu"})
                         :state {:values {:position-block
                                          {:position {:value "Editor"}}}}}})
        response (slack/handle-interaction "xoxb-token" send-email! record! payload)
        body (json/read-str (:body response) :key-fn keyword)]
    (is (= "errors" (:response_action body)))
    (is (contains? (:errors body) (keyword slack/position-block)))
    (testing "no decision is recorded when the email fails"
      (is (nil? @recorded)))))

(deftest decision-collapses-source-message
  (let [calls (atom [])]
    (with-redefs [triapply-portal.slack/slack-post!
                  (fn [_ method payload] (swap! calls conj [method payload]) {})]
      ;; Seed the cache as a block_actions button click would.
      (swap! @#'triapply-portal.slack/pending-messages assoc "111.222"
             {:channel "C1"
              :blocks [{:type "header" :text {:type "plain_text" :text "New application"}}
                       {:type "section" :block_id "s1"
                        :text {:type "mrkdwn" :text "*Name:* Jordan"}}
                       {:type "actions" :block_id "decision" :elements []}]})
      (slack/handle-interaction
       "xoxb"
       (constantly {:sent? true})
       (constantly nil)
       (json/write-str
        {:type "view_submission"
         :view {:private_metadata (json/write-str
                                   {:decision "accept" :submissionId "abc"
                                    :name "Jordan" :email "j@drexel.edu"
                                    :channel "C1" :ts "111.222"})
                :state {:values {:position-block {:position {:value "News Writer"}}}}}})))
    (let [[method payload] (some #(when (= "chat.update" (first %)) %) @calls)]
      (is (= "chat.update" method))
      (is (= "C1" (:channel payload)))
      (is (= "111.222" (:ts payload)))
      (testing "the Accept/Deny actions block is removed"
        (is (not-any? #(= "decision" (:block_id %)) (:blocks payload)))
        (is (some #(= "s1" (:block_id %)) (:blocks payload)))) ; original detail kept
      (testing "a status line with the position is appended"
        (is (some #(and (= "context" (:type %))
                        (str/includes? (get-in % [:elements 0 :text]) "Accepted")
                        (str/includes? (get-in % [:elements 0 :text]) "News Writer"))
                  (:blocks payload)))))))

(deftest deny-email-failure-reopens-modal-with-banner
  (let [send-email! (constantly {:sent? false :error "smtp down"})
        payload (json/write-str
                 {:type "view_submission"
                  :view {:private_metadata (json/write-str {:decision "deny"
                                                            :submissionId "z-9"
                                                            :name "Sam Rivers"
                                                            :email "x@drexel.edu"})
                         :state {:values {}}}})
        response (slack/handle-interaction "xoxb-token" send-email! (constantly nil) payload)
        body (json/read-str (:body response) :key-fn keyword)]
    (testing "deny has no input block, so it updates the view instead of field errors"
      (is (= "update" (:response_action body)))
      (is (= "modal" (get-in body [:view :type]))))))
