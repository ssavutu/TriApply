(defproject triapply "0.1.0-SNAPSHOT"
  :description "FIXME: write description"
  :url "https://example.com/FIXME"
  :license {:name "EPL-2.0 OR GPL-2.0-or-later WITH Classpath-exception-2.0"
            :url "https://www.eclipse.org/legal/epl-2.0/"}
  :dependencies [[org.clojure/clojure "1.12.2"]
                 [ring/ring-core "1.15.2"]
                 [ring/ring-jetty-adapter "1.15.2"]
                 [ring/ring-devel "1.15.2"]
                 [metosin/reitit "0.9.1"]
                 [hiccup "2.0.0-RC4"]]
  :main ^:skip-aot triapply.core
  :target-path "target/%s"
  :profiles {:dev {:source-paths ["dev"]}
             :uberjar {:aot :all
                       :jvm-opts ["-Dclojure.compiler.direct-linking=true"]}})
