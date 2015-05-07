(defproject com.mdrogalis/onyx-metrics "0.6.0-alpha2"
  :description "Instrument Onyx workflows"
  :url "https://github.com/MichaelDrogalis/onyx"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.clojure/clojure "1.6.0"]
                 [com.mdrogalis/rotating-seq "0.1.3"]
                 [com.taoensso/timbre "3.0.1"]
                 [stylefruits/gniazdo "0.4.0"]]
  :profiles {:dev {:dependencies [[com.mdrogalis/onyx "0.6.0-alpha2"]
                                  [midje "1.6.3"]]
                   :plugins [[lein-midje "3.1.1"]]}})
