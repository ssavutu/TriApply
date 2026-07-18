(defproject triapply-portal "0.1.0-SNAPSHOT"
  :description "Public application portal for The Triangle's TriApply ATS"
  :license {:name "EPL-2.0 OR GPL-2.0-or-later WITH Classpath-exception-2.0"
            :url "https://www.eclipse.org/legal/epl-2.0/"}
  :dependencies [[org.clojure/clojure "1.12.2"]
                 [ring/ring-core "1.15.2"]
                 [ring/ring-jetty-adapter "1.15.2"]
                 [metosin/reitit-ring "0.9.1"]
                 [org.clojure/data.json "2.5.1"]
                 [com.sun.mail/jakarta.mail "2.0.1"]
                 [hiccup "2.0.0-RC4"]]
  :main ^:skip-aot triapply-portal.main
  :target-path "target/%s"
  :profiles {:dev {:source-paths ["dev"]
                   :dependencies [[ring/ring-devel "1.15.2"]]}
             :uberjar {:aot :all
                       :jvm-opts ["-Dclojure.compiler.direct-linking=true"]}})
