(defproject org.onyxplatform/onyx-metrics "0.7.6-SNAPSHOT"
  :description "Instrument Onyx workflows"
  :url "https://github.com/MichaelDrogalis/onyx"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.clojure/clojure "1.7.0"]
                 [com.mdrogalis/rotating-seq "0.1.3"]
                 [stylefruits/gniazdo "0.4.0"]]
  :profiles {:dev {:dependencies [^{:voom {:repo "git@github.com:onyx-platform/onyx.git" :branch "master"}}
                                  [org.onyxplatform/onyx "0.7.5"]
                                  [org.clojure/core.async "0.1.346.0-17112a-alpha"]
                                  [riemann-clojure-client "0.4.1"]
                                  [com.taoensso/timbre "4.1.1"]
                                  [midje "1.7.0"]]
                   :plugins [[lein-midje "3.1.3"]]}})
