(ns triapply-portal.ats-client
  (:require
   [clojure.java.io :as io]
   [clojure.string :as str])
  (:import
   [java.io File]
   [java.net URI]
   [java.net.http HttpClient HttpRequest HttpRequest$BodyPublishers
    HttpResponse$BodyHandlers]
   [java.nio.charset StandardCharsets]
   [java.nio.file Files]
   [java.time Duration]
   [java.util UUID]))

(defn- upload? [value]
  (and (map? value) (instance? File (:tempfile value))))

(defn- safe-header-value [value]
  (-> (str value)
      (str/replace #"[\"\r\n]" "_")))

(defn- safe-filename [filename]
  (let [candidate (some-> filename str io/file .getName)]
    (if (str/blank? candidate)
      "upload"
      candidate)))

(defn- endpoint-url [base-url]
  (str (str/replace base-url #"/+$" "") "/api/v1/applications"))

(defn- utf8-bytes [value]
  (.getBytes (str value) StandardCharsets/UTF_8))

(defn- normalize-uploads [value]
  (letfn [(normalize [files value]
            (cond
              (upload? value)
              (let [upload-id (str (UUID/randomUUID))
                    part-name (str "upload-" upload-id)
                    metadata {:upload-id upload-id
                              :upload-part part-name
                              :filename (safe-filename (:filename value))
                              :content-type (:content-type value)
                              :size (:size value)}]
                [(conj files (assoc value
                                     :upload-id upload-id
                                     :part-name part-name
                                     :safe-filename (:filename metadata)))
                 metadata])

              (map? value)
              (reduce-kv
               (fn [[files normalized] k v]
                 (let [[files item] (normalize files v)]
                   [files (assoc normalized k item)]))
               [files {}]
               value)

              (sequential? value)
              (reduce
               (fn [[files normalized] item]
                 (let [[files item] (normalize files item)]
                   [files (conj normalized item)]))
               [files []]
               value)

              :else
              [files value]))]
    (normalize [] value)))

(defn- field-part [boundary name value]
  [(utf8-bytes (str "--" boundary "\r\n"
                    "Content-Disposition: form-data; name=\"" (safe-header-value name) "\"\r\n"
                    "Content-Type: text/plain; charset=utf-8\r\n\r\n"))
   (utf8-bytes value)
   (utf8-bytes "\r\n")])

(defn- file-part [boundary {:keys [part-name safe-filename content-type tempfile]}]
  [(utf8-bytes (str "--" boundary "\r\n"
                    "Content-Disposition: form-data; name=\"" (safe-header-value part-name)
                    "\"; filename=\"" (safe-header-value safe-filename) "\"\r\n"
                    "Content-Type: " (or content-type "application/octet-stream") "\r\n\r\n"))
   (Files/readAllBytes (.toPath ^File tempfile))
   (utf8-bytes "\r\n")])

(defn- multipart-body [application]
  (let [boundary (str "triapply-" (UUID/randomUUID))
        [files normalized] (normalize-uploads application)
        chunks (vec
                (concat
                 (field-part boundary "application-edn" (pr-str normalized))
                 (mapcat #(file-part boundary %) files)
                 [(utf8-bytes (str "--" boundary "--\r\n"))]))]
    {:boundary boundary
     :publisher (HttpRequest$BodyPublishers/ofByteArrays chunks)}))

(defn submit-application!
  ([base-url application]
   (submit-application! base-url 10000 application))
  ([base-url timeout-millis application]
   (let [{:keys [boundary publisher]} (multipart-body application)
         request (-> (HttpRequest/newBuilder (URI/create (endpoint-url base-url)))
                     (.timeout (Duration/ofMillis timeout-millis))
                     (.header "Content-Type" (str "multipart/form-data; boundary=" boundary))
                     (.POST publisher)
                     .build)
         response (.send (HttpClient/newHttpClient)
                         request
                         (HttpResponse$BodyHandlers/ofString))]
     {:accepted? (<= 200 (.statusCode response) 299)
      :status (.statusCode response)
      :body (.body response)})))
