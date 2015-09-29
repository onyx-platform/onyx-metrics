(ns onyx.lifecycle.metrics.riemann
  (:require [clojure.core.async :refer [chan >!! <!! dropping-buffer]]
            [riemann.client :as r]
            [taoensso.timbre :refer [info warn fatal]]
            [onyx.lifecycle.metrics.metrics :as metrics]
            [interval-metrics.core :as im]))

(defn start-riemann-sender [address port send-timeout ch]
  (future
    (let [client (r/tcp-client {:host address :port port})]
      (loop []
        (when-let [event (<!! ch)]
          (try
            (info "Trying to send " event)
            ;@(r/send-event client event)
            (catch InterruptedException e
              ;; Intentionally pass.
              )
            (catch Throwable e
              ;; Retry message
              ;; Replace with core.async offer when it is part of core.async
              ;; If the metrics buffer is already full then there's no point adding to the problem
              (>!! ch event)
              (warn e))))
        (recur)))))

(def historical-throughput-max-count 1000)
(def latency-period-secs 10)

(defn before-task [event {:keys [riemann/address riemann/port] :as lifecycle}]
  ;; Dropping is better than blocking and the metrics timings going awry
  (let [ch (chan (dropping-buffer (or (:riemann/buffer-capacity lifecycle) 10000)))
        riemann-send-timeout (or (:riemann/send-timeout lifecycle) 5000)
        throughputs (atom (list))
        metrics (metrics/build-metrics)]
    {:onyx.metrics.riemann/metrics metrics
     :onyx.metrics.riemann/sender-thread (start-riemann-sender address port riemann-send-timeout ch)
     :onyx.metrics.riemann/riemann-fut
     (future
       (loop [cycle-count 0 sleep-time 1000]
         (let [time-start (System/currentTimeMillis)]
           (try
             (Thread/sleep sleep-time)
             (let [name (str (:riemann/workflow-name lifecycle))
                   task-name (str (:onyx.core/task event))]
               (assert task-name ":riemann/workflow-name must be defined")

               (let [throughput (im/snapshot! (:rate metrics))
                     throughputs-val (swap! throughputs (fn [tps]
                                                          (conj (take (dec historical-throughput-max-count) 
                                                                      tps)
                                                                throughput)))] 

                 (>!! ch {:service (format "[%s] 1s_throughput" task-name)
                          :state "ok" :metric (apply + (take 1 throughputs-val))
                          :tags ["throughput_1s" "onyx" task-name name]})

                 (>!! ch {:service (format "[%s] 10s_throughput" task-name)
                          :state "ok" :metric (apply + (take 10 throughputs-val))
                          :tags ["throughput_10s" "onyx" task-name name]})

                 (>!! ch {:service (format "[%s] 60s_throughput" task-name)
                          :state "ok" :metric (apply + (take 60 throughputs-val))
                          :tags ["throughput_60s" "onyx" task-name name]}))

               (when (= cycle-count 0)
                 (when-let [rate+latency (:rate+latency-10s metrics)]
                   (let [latency-snapshot (im/snapshot! rate+latency) 
                         latencies-vals (->> latency-snapshot 
                                             :latencies
                                             (map (juxt key (fn [kv] 
                                                              (float (/ (val kv) 1000000.0)))))
                                             (into {}))]
                     (>!! ch {:service (format "[%s] 50_percentile_latency" task-name)
                              :state "ok" :metric (get latencies-vals 0.5)
                              :tags ["latency_50th" "onyx" "50_percentile" task-name name]})

                     (>!! ch {:service (format "[%s] 90_percentile_latency" task-name)
                              :state "ok" :metric (get latencies-vals 0.90)
                              :tags ["latency_90th" "onyx" task-name name]})

                     (>!! ch {:service (format "[%s] 99_percentile_latency" task-name)
                              :state "ok" :metric (get latencies-vals 0.99)
                              :tags ["latency_99th" "onyx" task-name name]})

                     (>!! ch {:service (format "[%s] 99.9_percentile_latency" task-name)
                              :state "ok" :metric (get latencies-vals 0.999)
                              :tags ["latency_99.9th" "onyx" task-name name]})))))
             (catch InterruptedException e)
             (catch Throwable e
               (fatal e)))
           (recur (mod (inc cycle-count) latency-period-secs)
                  (max 0 (- 1000 (- (System/currentTimeMillis) time-start)))))))}))

(defn after-task [event lifecycle]
  (future-cancel (:onyx.metrics.riemann/riemann-fut event))
  (future-cancel (:onyx.metrics.riemann/sender-thread event))
  {})

(def calls
  {:lifecycle/before-task-start before-task
   :lifecycle/after-ack-segment (metrics/build-on-completion :onyx.metrics.riemann/metrics)
   :lifecycle/after-retry-segment (metrics/build-on-retry :onyx.metrics.timbre/metrics) 
   :lifecycle/before-batch (metrics/build-before-batch :onyx.metrics.riemann/metrics)
   :lifecycle/after-batch (metrics/build-after-batch :onyx.metrics.riemann/metrics)
   :lifecycle/after-task-stop after-task})
