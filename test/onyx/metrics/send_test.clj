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
            [onyx.lifecycle.metrics.riemann]
            [onyx.lifecycle.metrics.websocket]
            [onyx.api]))

(def n-messages 100000)

(defn my-inc [{:keys [n] :as segment}]
  (assoc segment :n (inc n)))

(deftest metrics-test
  (doseq [sender [:onyx.lifecycle.metrics.websocket/websocket-sender
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


    (let [events (atom [])] 
      (with-redefs [riemann.client/tcp-client (fn [opts] nil)
                    riemann.client/send-event (fn [_ event] 
                                                (swap! events conj event)
                                                (future :sent))
                    ;taoensso.timbre/info (fn [& vs]
                    ;                       (swap! events conj :print))
                    gniazdo.core/connect (fn [_])
                    gniazdo.core/send-msg (fn [_ v]
                                            (swap! events conj v))] 
        (let [id (java.util.UUID/randomUUID)
              env-config {:zookeeper/address "127.0.0.1:2188"
                          :zookeeper/server? true
                          :zookeeper.server/port 2188
                          :onyx/id id}
              peer-config {:zookeeper/address "127.0.0.1:2188"
                           :onyx/id id
                           :onyx.peer/job-scheduler :onyx.job-scheduler/greedy
                           :onyx.messaging/impl :aeron
                           :onyx.messaging/allow-short-circuit? true
                           :onyx.messaging/peer-port-range [40200 40260]
                           :onyx.messaging/bind-addr "localhost"}]
          (with-test-env [test-env [3 env-config peer-config]]
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
                              {:lifecycle/task :in
                               :lifecycle/calls :onyx.plugin.core-async/reader-calls}

                              {:lifecycle/task :all
                               :lifecycle/calls :onyx.lifecycle.metrics.metrics/calls
                               :websocket/address "ws://127.0.0.1:3000/metrics"
                               :metrics/buffer-capacity 10000
                               :metrics/workflow-name "test-workflow"
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
                  _ (onyx.api/submit-job peer-config
                                         {:catalog catalog
                                          :workflow workflow
                                          :lifecycles lifecycles
                                          :task-scheduler :onyx.task-scheduler/balanced})
                  results (take-segments! out-chan)
                  end-time (System/currentTimeMillis)]
              (let [expected (set (map (fn [x] {:n (inc x)}) (range n-messages)))]
                (is (= expected (set (butlast results))))
                (is (= :done (last results)))
                (is (> (count @events) (* 3 ; number of tasks
                                          (/ (- end-time start-time) 1000)
                                          ;; 4 events every second + 8 events ever 10 seconds, round down a little
                                          4.6)))))))))))
