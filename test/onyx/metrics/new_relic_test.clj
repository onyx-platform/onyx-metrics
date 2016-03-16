(ns onyx.metrics.new-relic-test
  (:require [clojure.core.async :refer [chan >!! <!! close! sliding-buffer]]
            [clojure.test :refer [deftest is testing]]
            [onyx.plugin.core-async :refer [take-segments!]]
            [onyx.test-helper :refer [with-test-env add-test-env-peers!]]
            [riemann.client]
            [gniazdo.core]
            [taoensso.timbre :refer [info warn fatal]]
            [onyx.lifecycle.metrics.metrics]
            [onyx.lifecycle.metrics.timbre]
            [onyx.monitoring.events :as monitoring]
            [onyx.metrics.newrelic]
            [onyx.lifecycle.metrics.riemann :as riemann]
            [onyx.lifecycle.metrics.websocket]
            [onyx.api]))

(def n-messages 100000)

(defn my-inc [{:keys [n] :as segment}]
  (assoc segment :n (inc n)))

(def failure? (atom false))

(deftest metrics-test
  (doseq [sender [:onyx.metrics.newrelic/newrelic-sender]]

    (def in-chan (chan (inc n-messages)))

    (def out-chan (chan (sliding-buffer (inc n-messages))))

    (defn inject-in-ch [event lifecycle]
      {:core.async/chan in-chan})

    (defn inject-out-ch [event lifecycle]
      {:core.async/chan out-chan})

    (def in-calls
      {:lifecycle/before-task-start inject-in-ch})

    (def out-calls
      {:lifecycle/before-task-start inject-out-ch})

    (let [events-atom (atom [])]
      (with-redefs [onyx.metrics.newrelic/warn-failure (fn [a b]
                                                         (println "Failed request " a b)
                                                         (reset! failure? true))]
        (let [id (java.util.UUID/randomUUID)
              env-config {:zookeeper/address "127.0.0.1:2188"
                          :zookeeper/server? true
                          :zookeeper.server/port 2188
                          :onyx/tenancy-id id}
              peer-config {:zookeeper/address "127.0.0.1:2188"
                           :onyx/tenancy-id id
                           :onyx.peer/job-scheduler :onyx.job-scheduler/greedy
                           :onyx.messaging/impl :aeron
                           :onyx.messaging/allow-short-circuit? false
                           :onyx.messaging/peer-port 40200
                           :onyx.messaging/bind-addr "localhost"}
              ;; TODO, doesn't currently test monitoring
              ]
          (with-test-env [test-env [3 env-config peer-config]]
            (let [batch-size 20
                  catalog [{:onyx/name :in
                            :onyx/plugin :onyx.plugin.core-async/input
                            :onyx/type :input
                            :onyx/medium :core.async
                            :onyx/batch-size batch-size
                            :onyx/max-pending 1000
                            :onyx/max-peers 1
                            :onyx/doc "Reads segments from a core.async channel"}

                           {:onyx/name :inc
                            :onyx/fn ::my-inc
                            :onyx/type :function
                            :onyx/batch-size batch-size}

                           {:onyx/name :out
                            :onyx/plugin :onyx.plugin.core-async/output
                            :onyx/type :output
                            :onyx/medium :core.async
                            :onyx/batch-size batch-size
                            :onyx/max-peers 1
                            :onyx/doc "Writes segments to a core.async channel"}]
                  workflow [[:in :inc] [:inc :out]]
                  lifecycles [{:lifecycle/task :in
                               :lifecycle/calls ::in-calls}
                              {:lifecycle/task :in
                               :lifecycle/calls :onyx.plugin.core-async/reader-calls}

                              {:lifecycle/task :all
                               :lifecycle/calls :onyx.lifecycle.metrics.metrics/calls
                               :metrics/sender-fn sender
                               :lifecycle/doc "Instruments a task's metrics"}

                              {:lifecycle/task :out
                               :lifecycle/calls ::out-calls}
                              {:lifecycle/task :out
                               :lifecycle/calls :onyx.plugin.core-async/writer-calls}]

                  _ (doseq [n (range n-messages)]
                      (>!! in-chan {:n n}))
                  _ (>!! in-chan :done)
                  _ (close! in-chan)
                  start-time (System/currentTimeMillis)
                  job (onyx.api/submit-job peer-config
                                           {:catalog catalog
                                            :metadata {:name "test-workflow"}
                                            :workflow workflow
                                            :lifecycles lifecycles
                                            :task-scheduler :onyx.task-scheduler/balanced})
                  results (take-segments! out-chan)
                  _ (onyx.api/await-job-completion peer-config (:job-id job))]
              (is (not @failure?)))))))))
