(ns onyx.lifecycle.metrics.metrics
  (:require [clojure.core.async :refer [chan >!! <!! dropping-buffer]]
            [taoensso.timbre :refer [info warn fatal]]
            [clojure.set :refer [rename-keys]]
            [onyx.peer.operation :refer [kw->fn]]
            [interval-metrics.core :as im]))

(def historical-throughput-max-count 1000)
(def latency-period-secs 10)

(defrecord Metrics [rate batch-start rate+latency-10s retry-rate completion-tracking completion-rate+latencies-10s])

(defn before-task [event lifecycle]
  (when (:onyx.metrics.metrics/metrics event)
    (throw (ex-info "Only one metrics lifecyle is supported per task." lifecycle)))

  ;; Dropping is better than blocking and the metrics timings going awry
  (let [ch (chan (dropping-buffer (or (:metrics/buffer-capacity lifecycle) 10000)))
        throughputs (atom (list))
        rate (im/rate)
        retry-rate (im/rate)
        rate+latency-10s (im/rate+latency {:rate-unit :nanoseconds 
                                           :latency-unit :nanoseconds
                                           :quantiles [0.5 0.90 0.95 0.99 0.999]})

        completion-rate+latencies-10s (im/rate+latency {:rate-unit :nanoseconds 
                                                        :latency-unit :nanoseconds
                                                        :quantiles [0.5 0.90 0.95 0.99 0.999]})
        batch-start (atom nil)
        metrics (->Metrics rate batch-start rate+latency-10s retry-rate (atom {}) completion-rate+latencies-10s)
        name (str (:metrics/workflow-name lifecycle))
        task-name (str (:onyx.core/task event))
        core {:job-id (:onyx.core/job-id event)
              :task-id (:onyx.core/task-id event)
              :task-name (:onyx.core/task event)
              :peer-id (:onyx.core/id event)}]

    {:onyx.metrics.metrics/metrics metrics
     :onyx.metrics.metrics/sender-thread ((kw->fn (:metrics/sender-fn lifecycle)) lifecycle ch)
     :onyx.metrics.metrics/metrics-fut
     (future
       (try
         (loop [cycle-count 0 sleep-time 1000]
           (Thread/sleep sleep-time)
           (let [time-start (System/currentTimeMillis)]
             (let [throughput (im/snapshot! (:rate metrics))
                   throughputs-val (swap! throughputs (fn [tps]
                                                        (conj (take (dec historical-throughput-max-count) 
                                                                    tps)
                                                              throughput)))] 
               (>!! ch (merge core {:service (format "[%s] 1s_throughput" task-name)
                                    :window "1s"
                                    :metric :throughput
                                    :value (apply + (remove nil? (take 1 throughputs-val)))
                                    :tags ["throughput_1s" "onyx" task-name name]}))

               (>!! ch (merge core {:service (format "[%s] 10s_throughput" task-name)
                                    :window "10s"
                                    :metric :throughput
                                    :value (apply + (remove nil? (take 10 throughputs-val)))
                                    :tags ["throughput_10s" "onyx" task-name name]}))

               (>!! ch (merge core {:service (format "[%s] 60s_throughput" task-name)
                                    :window "60s"
                                    :metric :throughput
                                    :value (apply + (remove nil? (take 60 throughputs-val)))
                                    :tags ["throughput_60s" "onyx" task-name name]})))

               (let [retry-rate-val (im/snapshot! (:retry-rate metrics))]
                 (>!! ch (merge core {:service (format "[%s] 1s_retry-segment-rate" task-name)
                                      :window "1s"
                                      :metric :retry-rate
                                      :value retry-rate-val 
                                      :tags ["retry_segment_rate_1s" "onyx" task-name name]})))

               (when (= cycle-count 0)
                 (when (= :input (:onyx/type (:onyx.core/task-map event)))
                   (let [completion-rate+latency (:completion-rate+latencies-10s metrics)
                         completion-latency-snapshot (im/snapshot! completion-rate+latency) 
                         latencies-vals (->> completion-latency-snapshot 
                                             :latencies
                                             (map (juxt key (fn [kv] 
                                                              (when-let [v (val kv)] 
                                                                (float (/ v 1000000.0))))))
                                             (into {}))]
                     (>!! ch (merge core {:service (format "[%s] 50.0th_percentile_complete_latency" task-name)
                                          :window "10s"
                                          :metric :complete-latency
                                          :quantile 0.50
                                          :value (get latencies-vals 0.5)
                                          :tags ["complete_latency_50th" "onyx" "50_percentile" task-name name]}))

                     (>!! ch (merge core {:service (format "[%s] 90.0th_percentile_complete_latency" task-name)
                                          :window "10s"
                                          :quantile 0.90
                                          :metric :complete-latency
                                          :value (get latencies-vals 0.90)
                                          :tags ["complete_latency_90th" "onyx" task-name name]}))

                     (>!! ch (merge core {:service (format "[%s] 99.0th_percentile_complete_latency" task-name)
                                          :window "10s"
                                          :quantile 0.99
                                          :metric :complete-latency
                                          :value (get latencies-vals 0.99)
                                          :tags ["complete_latency_99th" "onyx" task-name name]}))

                     (>!! ch (merge core {:service (format "[%s] 99.9th_percentile_complete_latency" task-name)
                                          :window "10s"
                                          :quantile 0.999
                                          :metric :complete-latency
                                          :value (get latencies-vals 0.999)
                                          :tags ["complete_latency_99.9th" "onyx" task-name name]}))))

                 (let [rate+latency (:rate+latency-10s metrics)
                       latency-snapshot (im/snapshot! rate+latency) 
                       latencies-vals (->> latency-snapshot 
                                           :latencies
                                           (map (juxt key (fn [kv] 
                                                            (when-let [v (val kv)] 
                                                              (float (/ v 1000000.0))))))
                                           (into {}))]
                   (>!! ch (merge core {:service (format "[%s] 50_percentile_batch_latency" task-name) 
                                        :window "10s"
                                        :quantile 0.50
                                        :metric :batch-latency
                                        :value (get latencies-vals 0.5)
                                        :tags ["batch_latency_50th" "onyx" "50_percentile" task-name name]}))

                   (>!! ch (merge core {:service (format "[%s] 90_percentile_batch_latency" task-name) 
                                        :window "10s"
                                        :quantile 0.90
                                        :value (get latencies-vals 0.90)
                                        :metric :batch-latency
                                        :tags ["batch_latency_90th" "onyx" task-name name]}))

                   (>!! ch (merge core {:service (format "[%s] 99_percentile_batch_latency" task-name)
                                        :window "10s"
                                        :quantile 0.99
                                        :value (get latencies-vals 0.99)
                                        :metric :batch-latency
                                        :tags ["batch_latency_99th" "onyx" task-name name]}))

                   (>!! ch (merge core {:service (format "[%s] 99.9_percentile_batch_latency" task-name)
                                        :window "10s"
                                        :quantile 0.999
                                        :value (get latencies-vals 0.999)
                                        :metric :batch-latency
                                        :tags ["batch_latency_99.9th" "onyx" task-name name]}))))
             (recur (mod (inc cycle-count) latency-period-secs)
                    (max 0 (- 1000 (- (System/currentTimeMillis) time-start))))))
         (catch InterruptedException e)
         (catch Throwable e
           (fatal e))))}))

(defn before-batch [event lifecycle]
  ;; swap into atom so we only need one event map lookup later
  (reset! (:batch-start (:onyx.metrics.metrics/metrics event)) (System/nanoTime))
  {})

(defn after-batch [event lifecycle]
    (let [{:keys [rate batch-start rate+latency-10s completion-tracking]} (:onyx.metrics.metrics/metrics event)
          timestamp (System/nanoTime)
          latency (- timestamp @batch-start)
          batch (:onyx.core/batch event)]
      (when (= (:onyx/type (:onyx.core/task-map event)) :input)
        (let [transducer (map (fn [v] (clojure.lang.MapEntry. (:id v) timestamp)))]
          (swap! completion-tracking into transducer batch)))
      (im/update! rate (count batch))
      (im/update! rate+latency-10s latency)
      {}))


(defn on-completion [event message-id rets lifecycle]
    (let [metric (:onyx.metrics.metrics/metrics event)
          completion-rate+latencies-10s (:completion-rate+latencies-10s metric)
          completion-tracking (:completion-tracking metric)] 
      (when-let [v (@completion-tracking message-id)]
        (im/update! completion-rate+latencies-10s (- ^long (System/nanoTime) ^long v))
        (swap! completion-tracking dissoc message-id))))

(defn on-retry [event message-id rets lifecycle]
  (let [metric (:onyx.metrics.metrics/metrics event)
        retry-rate (:retry-rate metric)] 
    (swap! (:completion-tracking metric) dissoc message-id)
    (im/update! retry-rate 1)))

(defn after-task [event lifecycle]
  (future-cancel (:onyx.metrics.metrics/metrics-fut event))
  (future-cancel (:onyx.metrics.metrics/sender-thread event))
  {})

(def calls
  {:lifecycle/before-task-start before-task
   :lifecycle/after-ack-segment on-completion
   :lifecycle/after-retry-segment on-retry 
   :lifecycle/before-batch before-batch 
   :lifecycle/after-batch after-batch
   :lifecycle/after-task-stop after-task})
