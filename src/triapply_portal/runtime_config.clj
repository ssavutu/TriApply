(ns triapply-portal.runtime-config)

(def default-http-port 4321)
(def default-ats-timeout-millis 10000)

(defn parse-http-port [value]
  (let [port (parse-long value)]
    (cond
      (nil? port)
      (throw (ex-info "PORT must be an integer." {:value value}))

      (<= 1 port 65535)
      port

      :else
      (throw (ex-info "PORT must be between 1 and 65535." {:value value})))))

(defn http-port []
  (if-let [value (System/getenv "PORT")]
    (parse-http-port value)
    default-http-port))

(defn parse-positive-int [name value]
  (let [parsed (parse-long value)]
    (if (and parsed (pos? parsed))
      parsed
      (throw (ex-info (str name " must be a positive integer.")
                      {:value value})))))

(defn ats-url []
  (System/getenv "TRIAPPLY_ATS_URL"))

(defn ats-timeout-millis []
  (if-let [value (System/getenv "TRIAPPLY_ATS_TIMEOUT_MILLIS")]
    (parse-positive-int "TRIAPPLY_ATS_TIMEOUT_MILLIS" value)
    default-ats-timeout-millis))
