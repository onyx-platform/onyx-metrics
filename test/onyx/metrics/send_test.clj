(ns onyx.metrics.send-test
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
            [onyx.lifecycle.metrics.riemann :as riemann]
            [onyx.lifecycle.metrics.websocket]
            [onyx.api])
  (:import [com.codahale.metrics JmxReporter]))

(def n-messages 100000)

(defn my-inc [{:keys [n] :as segment}]
  ;(println "INC")
  ;(Thread/sleep 5000)
  (assoc segment :n (inc n)))

(def valid-tag-combos
  #{["throughput_1s" "onyx" ":out" "test-workflow"]
    ["throughput_1s" "onyx" ":inc" "test-workflow"]
    ["throughput_1s" "onyx" ":in" "test-workflow"]
    ["throughput_10s" "onyx" ":inc" "test-workflow"]
    ["throughput_10s" "onyx" ":in" "test-workflow"]
    ["throughput_10s" "onyx" ":out" "test-workflow"]
    ["throughput_60s" "onyx" ":inc" "test-workflow"]
    ["throughput_60s" "onyx" ":in" "test-workflow"]
    ["throughput_60s" "onyx" ":out" "test-workflow"]

    ["batch_latency_max" "onyx" ":out" "test-workflow"]
    ["batch_latency_max" "onyx" ":inc" "test-workflow"]
    ["batch_latency_max" "onyx" ":in" "test-workflow"]
    ["batch_latency_99_9th" "onyx" ":in" "test-workflow"]
    ["batch_latency_99_9th" "onyx" ":inc" "test-workflow"]
    ["batch_latency_99_9th" "onyx" ":out" "test-workflow"]
    ["batch_latency_99th" "onyx" ":in" "test-workflow"]
    ["batch_latency_99th" "onyx" ":inc" "test-workflow"]
    ["batch_latency_99th" "onyx" ":out" "test-workflow"]
    ["batch_latency_90th" "onyx" ":inc" "test-workflow"]
    ["batch_latency_90th" "onyx" ":in" "test-workflow"]
    ["batch_latency_90th" "onyx" ":out" "test-workflow"]
    ["batch_latency_50th" "onyx" "50_percentile" ":out" "test-workflow"]
    ["batch_latency_50th" "onyx" "50_percentile" ":inc" "test-workflow"]
    ["batch_latency_50th" "onyx" "50_percentile" ":in" "test-workflow"]

    ["pending_messages_count" "onyx" ":in" "test-workflow"]

    ["complete_latency_max" "onyx" ":in" "test-workflow"]
    ["complete_latency_99_9th" "onyx" ":in" "test-workflow"]
    ["complete_latency_99th" "onyx" ":in" "test-workflow"]
    ["complete_latency_90th" "onyx" ":in" "test-workflow"]
    ["complete_latency_50th" "onyx" "50_percentile" ":in" "test-workflow"]

    []

    ["onyx" "peer.complete-message.latency max"]
    ["onyx" "peer.ack-segments.latency max"]

    ["monitoring-config" ":in"] 
    ["monitoring-config" ":out"]
    ["monitoring-config" ":inc"]

    ["retry_segment_rate_1s" "onyx" ":in" "test-workflow"]})

(deftest metrics-test
  (doseq [sender [;:onyx.lifecycle.metrics.websocket/websocket-sender

                  ;; cannot test timbre-sender as info is a macro?
                  ;:onyx.lifecycle.metrics.timbre/timbre-sender
                  :onyx.lifecycle.metrics.riemann/riemann-sender]] 

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
      (with-redefs [riemann.client/tcp-client (fn [opts] nil)
                    riemann.client/send-events (fn [_ events] 
                                                (swap! events-atom into events)
                                                (future :sent))
                    ;taoensso.timbre/info (fn [& vs]
                    ;                       (swap! events conj :print))
                    gniazdo.core/connect (fn [_])
                    gniazdo.core/send-msg (fn [_ v]
                                            (swap! events-atom conj v))] 
        (let [id (java.util.UUID/randomUUID)
              env-config {:zookeeper/address "127.0.0.1:2188"
                          :zookeeper/server? true
                          :zookeeper.server/port 2188
                          :onyx/tenancy-id id}
              monitoring-config (monitoring/monitoring-config)
              reporter (.build (JmxReporter/forRegistry (:registry monitoring-config)))
              _ (.start ^JmxReporter reporter)
              peer-config {:zookeeper/address "127.0.0.1:2188"
                           :onyx/tenancy-id id
                           :onyx.peer/job-scheduler :onyx.job-scheduler/greedy
                           :monitoring-config monitoring-config
                           :onyx.messaging/impl :aeron
                           :onyx.messaging/allow-short-circuit? false
                           :onyx.messaging/peer-port 40200
                           :onyx.messaging/bind-addr "localhost"}
              host-id (str (java.util.UUID/randomUUID))]
          (with-test-env [test-env [3 env-config peer-config monitoring-config]]
            (let [batch-size 20
                  catalog [{:onyx/name :in
                            :onyx/plugin :onyx.plugin.core-async/input
                            :onyx/type :input
                            :onyx/medium :core.async
                            :onyx/batch-size batch-size
                            :onyx/pending-timeout 5000
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
                               :websocket/address "ws://127.0.0.1:3000/metrics"
                               :metrics/buffer-capacity 10000
                               :riemann/address "localhost"
                               :riemann/port 12201
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
                  _ (onyx.api/await-job-completion peer-config (:job-id job))
                  end-time (System/currentTimeMillis)]
              (Thread/sleep 100000)
              (let [expected (set (map (fn [x] {:n (inc x)}) (range n-messages)))]
                (is (= expected (set (butlast results))))
                (is (= :done (last results)))
                (is (= valid-tag-combos (set (map (comp vec butlast :tags) @events-atom))))
                (is (nil? (some #(not (instance? java.lang.String %)) (mapcat :tags @events-atom))))
                (is (> (count @events-atom) (* 3 ; number of tasks
                                               (/ (- end-time start-time) 1000)
                                               ;; only approximate because of brittle test on CI
                                               3)))))))))))
