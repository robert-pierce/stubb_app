(defproject stubb_app "0.1.0-SNAPSHOT"
  :description "FIXME: write description"
  :url "http://example.com/FIXME"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.clojure/clojure "1.9.0"]
                 [org.clojure/core.async "0.3.443"]
                 [org.clojure/tools.logging "0.3.1"]
                 [org.clojure/data.zip "0.1.2"]
                 [org.clojure/data.xml "0.0.8"]
                 [org.clojure/data.json "0.2.6"]
                 [hickory "0.7.1"]
                 [http-kit "2.3.0-beta2"]
                 [cheshire "5.7.1"]]
  :main ^:skip-aot stubb-app.core
  :target-path "target/%s"
  :jvm-opts ["-Xmx4G"]
  :profiles {:uberjar {:aot :all}})
