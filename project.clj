(defproject org.onyxplatform/onyx-metrics "0.9.10.0-SNAPSHOT"
  :description "Instrument Onyx workflows"
  :url "https://github.com/onyx-platform/onyx-metrics"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :repositories {"snapshots" {:url "https://clojars.org/repo"
                              :username :env
                              :password :env
                              :sign-releases false}
                 "releases" {:url "https://clojars.org/repo"
                             :username :env
                             :password :env
                             :sign-releases false}}
  :dependencies [[org.onyxplatform/onyx "0.9.10-20160914_170614-gc947ce7"]
                 ^{:voom {:repo "git@github.com:onyx-platform/onyx.git" :branch "master"}}
                 [org.clojure/clojure "1.7.0"]
                 [interval-metrics "1.0.0"]]
  :java-opts ^:replace ["-server" "-Xmx3g"]
  :global-vars  {*warn-on-reflection* true
                 *assert* false
                 *unchecked-math* :warn-on-boxed}
  :profiles {:dev {:dependencies [[riemann-clojure-client "0.4.1"]
                                  [stylefruits/gniazdo "0.4.0"]
                                  [clj-http "2.1.0"]
                                  [cheshire "5.5.0"]
                                  [cognician/dogstatsd-clj "0.1.1"]]
                   :plugins [[lein-set-version "0.4.1"]
                             [lein-update-dependency "0.1.2"]
                             [lein-pprint "1.1.1"]]}})
