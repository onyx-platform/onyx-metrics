(defproject org.onyxplatform/onyx-metrics "0.7.5.3-SNAPSHOT"
  :description "Instrument Onyx workflows"
  :url "https://github.com/MichaelDrogalis/onyx"
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
  :dependencies [[org.clojure/clojure "1.7.0"]
                 [interval-metrics "1.0.0"]
                 [stylefruits/gniazdo "0.4.0"]]
  :profiles {:dev {:dependencies [^{:voom {:repo "git@github.com:onyx-platform/onyx.git" :branch "master"}}
                                  [org.onyxplatform/onyx "0.7.5"]
                                  [org.clojure/core.async "0.1.346.0-17112a-alpha"]
                                  [riemann-clojure-client "0.4.1"]
                                  [com.taoensso/timbre "4.1.1"]
                                  [midje "1.7.0"]]
                   :plugins [[lein-midje "3.1.3"]
                             [lein-set-version "0.4.1"]
                             [lein-pprint  "1.1.1"]]}})
