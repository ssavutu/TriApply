(ns triapply-portal.storage
  "Persists uploaded application files to disk and stamps each with a public URL
  (served from the files base host), so downstream sinks can link to them."
  (:require
   [clojure.java.io :as io]
   [clojure.string :as str]
   [triapply-portal.runtime-config :as runtime-config])
  (:import
   [java.io File]
   [java.nio.file Files StandardCopyOption]
   [java.util UUID]))

(defn- upload? [value]
  (and (map? value) (instance? File (:tempfile value))))

(defn- safe-segment
  "Reduces an untrusted string to a single safe path segment. Both submissionId
  (from a client-controlled hidden field) and filenames pass through here, so
  this is the guard against path traversal."
  [value]
  (let [cleaned (-> (str value) (str/replace #"[^A-Za-z0-9._-]" "_"))]
    (if (str/blank? cleaned) "file" cleaned)))

(defn- safe-filename [filename]
  (safe-segment (some-> filename io/file .getName)))

(defn- store!
  "Copies one upload's tempfile under base-dir/<submission>/<unique-filename> and
  returns its public URL."
  [base-dir base-url submission-id upload]
  (let [dir-name (safe-segment submission-id)
        file-name (str (subs (str (UUID/randomUUID)) 0 8) "-"
                       (safe-filename (:filename upload)))
        target-dir (io/file base-dir dir-name)
        target (io/file target-dir file-name)]
    (.mkdirs target-dir)
    (Files/copy (.toPath ^File (:tempfile upload))
                (.toPath target)
                (into-array StandardCopyOption [StandardCopyOption/REPLACE_EXISTING]))
    (str (str/replace base-url #"/+$" "") "/" dir-name "/" file-name)))

(defn- walk [f value]
  (cond
    (upload? value) (f value)
    (map? value) (into {} (map (fn [[k v]] [k (walk f v)])) value)
    (sequential? value) (mapv #(walk f %) value)
    :else value))

(defn persist-uploads
  "Copies every uploaded file into the configured storage directory and assocs a
  public :url onto each upload map (leaving :tempfile intact so the ATS sink can
  still stream the bytes). Returns the application unchanged when no upload
  directory is configured, or for any individual file that fails to store."
  [{:keys [submission-id] :as application}]
  (if-let [base-dir (runtime-config/upload-dir)]
    (let [base-url (runtime-config/file-base-url)]
      (walk (fn [upload]
              (try
                (assoc upload :url (store! base-dir base-url submission-id upload))
                (catch Exception _ upload)))
            application))
    application))
