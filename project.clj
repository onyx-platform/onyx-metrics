(defproject org.onyxplatform/onyx-metrics "0.8.0.3"
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
  :java-opts ^:replace ["-server" "-Xmx3g"]
  :global-vars  {*warn-on-reflection* true 
                 *assert* false
                 *unchecked-math* :warn-on-boxed}
  :profiles {:dev {:dependencies [^{:voom {:repo "git@github.com:onyx-platform/onyx.git" :branch "master"}}
                                  [org.onyxplatform/onyx "0.8.0"]
                                  [riemann-clojure-client "0.4.1"]]
                   :plugins [[lein-set-version "0.4.1"]
                             [lein-update-dependency "0.1.2"]
                             [lein-pprint "1.1.1"]]}})
