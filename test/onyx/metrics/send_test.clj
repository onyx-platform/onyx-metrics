(ns onyx.metrics.send-test
  (:require [clojure.core.async :refer [chan >!! <!! close! sliding-buffer]]
            [clojure.test :refer [deftest is testing]]
            [onyx.plugin.core-async :refer [take-segments!]]
            [onyx.test-helper :refer [with-test-env add-test-env-peers!]]
            [riemann.client]
            [gniazdo.core]
            [com.stuartsierra.component :as component]
            [taoensso.timbre :refer [info warn fatal]]
            [onyx.monitoring.events :as monitoring]
            [onyx.lifecycle.metrics.timbre]
            [onyx.lifecycle.metrics.riemann :as riemann]
            [onyx.lifecycle.metrics.websocket]
            [clojure.java.jmx :as jmx]
            [onyx.api]))

(def n-messages 100000)

(defn my-inc [{:keys [n] :as segment}]
  ; (when (zero? (rand-int 100))
  ;   (Thread/sleep 100))
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

    ;[]
    ;["onyx" "peer.complete-message.latency max"]
    ;["onyx" "peer.ack-segments.latency max"]
    ;["monitoring-config" ":in"] 
    ;["monitoring-config" ":out"]
    ;["monitoring-config" ":inc"]

    ["retry_segment_rate_1s" "onyx" ":in" "test-workflow"]})

(deftest metrics-test
  (doseq [sender [;:onyx.lifecycle.metrics.websocket/websocket-sender

                  ;; cannot test timbre-sender as info is a macro?
                  ;:onyx.lifecycle.metrics.timbre/timbre-sender
                  :onyx.lifecycle.metrics.riemann/riemann-sender]] 

    (def in-chan (atom nil))
    (def in-buffer (atom nil))

    (def out-chan (chan (sliding-buffer (inc n-messages))))

    (defn inject-in-ch [event lifecycle]
      {:core.async/buffer in-buffer
       :core.async/chan @in-chan})

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
                    gniazdo.core/connect (fn [_])
                    gniazdo.core/send-msg (fn [_ v]
                                            (swap! events-atom conj v))] 
        (let [_ (reset! in-buffer {})
              _ (reset! in-chan (chan (inc n-messages)))
              id (java.util.UUID/randomUUID)
              env-config {:zookeeper/address "127.0.0.1:2188"
                          :zookeeper/server? true
                          :zookeeper.server/port 2188
                          :onyx/tenancy-id id}
              host-id (str (java.util.UUID/randomUUID))
              peer-config {:zookeeper/address "127.0.0.1:2188"
                           :onyx/tenancy-id id
                           :onyx.peer/job-scheduler :onyx.job-scheduler/greedy
                           :onyx.messaging/impl :aeron
                           :onyx.messaging/allow-short-circuit? false
                           :onyx.messaging/peer-port 40200
                           :onyx.messaging/bind-addr "localhost"}]
          (with-test-env [test-env [3 env-config peer-config monitoring-config]]
            (let [batch-size 20
                  catalog [{:onyx/name :in
                            :onyx/plugin :onyx.plugin.core-async/input
                            :onyx/type :input
                            :onyx/medium :core.async
                            :onyx/batch-size batch-size
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

                              {:lifecycle/task :all
                               :lifecycle/calls :onyx.lifecycle.metrics.metrics/calls
                               :metrics/lifecycles #{:lifecycle/apply-fn 
                                                     :lifecycle/unblock-subscribers
                                                     :lifecycle/write-batch
                                                     :lifecycle/read-batch}
                               :riemann/address "localhost"
                               :riemann/port 12201
                               :metrics/sender-fn sender
                               :lifecycle/doc "Instruments a task's metrics"}

                              {:lifecycle/task :out
                               :lifecycle/calls ::out-calls}]

                  _ (doseq [n (range n-messages)]
                      (>!! @in-chan {:n n}))
                  start-time (System/currentTimeMillis)
                  job (onyx.api/submit-job peer-config
                                           {:catalog catalog
                                            :metadata {:name "test-workflow"}
                                            :workflow workflow
                                            :lifecycles lifecycles
                                            :task-scheduler :onyx.task-scheduler/balanced})
                  _ (Thread/sleep 1000)
                  _ (is (> (count (jmx/mbean-names "metrics:*")) 50))
                  _ (close! @in-chan)
                  _ (onyx.test-helper/feedback-exception! peer-config (:job-id job))
                  results (take-segments! out-chan 50)
                  end-time (System/currentTimeMillis)]
              (let [expected (set (map (fn [x] {:n (inc x)}) (range n-messages)))]
                (is (= expected (set results)))))))))))
