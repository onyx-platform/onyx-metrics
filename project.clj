(defproject org.onyxplatform/onyx-metrics "0.7.0.1-SNAPSHOT"
  :description "Instrument Onyx workflows"
  :url "https://github.com/MichaelDrogalis/onyx"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.clojure/clojure "1.6.0"]
                 [com.mdrogalis/rotating-seq "0.1.3"]
                 [com.taoensso/timbre "3.0.1"]
                 [stylefruits/gniazdo "0.4.0"]
                 [riemann-clojure-client "0.4.1"]]
  :profiles {:dev {:dependencies [[org.onyxplatform/onyx "0.7.0"]
                                  [midje "1.6.3"]]
                   :plugins [[lein-midje "3.1.1"]]}})
