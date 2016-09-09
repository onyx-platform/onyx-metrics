(ns onyx.lifecycle.metrics.metrics
  (:require [clojure.core.async :refer [chan >!! <!! dropping-buffer]]
            [taoensso.timbre :refer [info warn fatal]]
            [clojure.set :refer [rename-keys]]
            [onyx.static.util :refer [kw->fn]]
            [interval-metrics.core :as im]))

(def historical-throughput-max-count 1000)
(def latency-period-secs 10)

(defrecord Metrics [rate batch-start rate+latency-10s retry-rate completion-tracking completion-rate+latencies-10s pending-size])

(defn before-task [event lifecycle]
  (when (:onyx.metrics.metrics/metrics event)
    (throw (ex-info "Only one metrics lifecyle is supported per task." lifecycle)))

  (info "name " (keys lifecycle))
  (when (:metrics/workflow-name lifecycle)
    (throw (ex-info ":metrics/workflow-name has been deprecated. Use job metadata such as:
                    {:workflow ...
                    :lifecycles ...
                    :metadata {:name \"YOURJOBNAME\"}}
                    to supply your job's name" {})))

  ;; Dropping is better than blocking and the metrics timings going awry
  (let [ch (chan (dropping-buffer (or (:metrics/buffer-capacity lifecycle) 10000)))
        throughputs (atom (list))
        rate (im/rate)
        retry-rate (im/rate)
        rate+latency-10s (im/rate+latency {:rate-unit :nanoseconds
                                           :latency-unit :nanoseconds
                                           :quantiles [0.5 0.90 0.95 0.99 0.999 1.0]})

        completion-rate+latencies-10s (im/rate+latency {:rate-unit :nanoseconds
                                                        :latency-unit :nanoseconds
                                                        :quantiles [0.5 0.90 0.95 0.99 0.999 1.0]})
        batch-start (atom nil)
        pending-size (atom 0)
        completion-tracking (atom {})
        metrics (->Metrics rate batch-start rate+latency-10s retry-rate completion-tracking completion-rate+latencies-10s pending-size)
        job-id (:onyx.core/job-id event)
        job-name (get-in event [:onyx.core/task-information :metadata :name] (str job-id))
        task-name (str (:onyx.core/task event))
        peer-id (:onyx.core/id event)
        peer-id-str (str peer-id)
        core {:job-id job-id 
              :job-name job-name
              :task-id (:onyx.core/task-id event)
              :task-name (:onyx.core/task event)
              :peer-id peer-id}]

    {:onyx.metrics.metrics/metrics metrics
     :onyx.metrics.metrics/send-ch ch
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
                                    :label "1s throughput"
                                    :window "1s"
                                    :period 1
                                    :metric :throughput
                                    :value (apply + (remove nil? (take 1 throughputs-val)))
                                    :tags ["throughput_1s" "onyx" task-name job-name peer-id-str]}))

               (>!! ch (merge core {:service (format "[%s] 10s_throughput" task-name)
                                    :window "10s"
                                    :label "10s throughput"
                                    :period 10
                                    :metric :throughput
                                    :value (apply + (remove nil? (take 10 throughputs-val)))
                                    :tags ["throughput_10s" "onyx" task-name job-name peer-id-str]}))

               (>!! ch (merge core {:service (format "[%s] 60s_throughput" task-name)
                                    :window "60s"
                                    :label "60s throughput"
                                    :period 60
                                    :metric :throughput
                                    :value (apply + (remove nil? (take 60 throughputs-val)))
                                    :tags ["throughput_60s" "onyx" task-name job-name peer-id-str]})))

                 (when (= :input (:onyx/type (:onyx.core/task-map event)))
                   (let [retry-rate-val (im/snapshot! (:retry-rate metrics))]
                     (>!! ch (merge core {:service (format "[%s] 1s_retry-segment-rate" task-name)
                                          :window "1s"
                                          :label "1s retry segment"
                                          :period 1
                                          :metric :retry-rate
                                          :value retry-rate-val 
                                          :tags ["retry_segment_rate_1s" "onyx" task-name job-name peer-id-str]})))

                   (>!! ch (merge core {:service (format "[%s] pending_messages_count" task-name)
                                        :metric :pending-messages-count
                                        :label "pending messages count"
                                        :value @pending-size
                                        :tags ["pending_messages_count" "onyx" task-name job-name peer-id-str]}))

                   (when (= cycle-count 0) ; only snapshot every 10s
                     (let [completion-rate+latency (:completion-rate+latencies-10s metrics)
                           completion-latency-snapshot (im/snapshot! completion-rate+latency)
                           latencies-vals (->> completion-latency-snapshot
                                               :latencies
                                               (map (juxt key (fn [kv]
                                                                (when-let [v (val kv)]
                                                                  (float (/ v 1000000.0))))))
                                               (into {}))]

                       (>!! ch (merge core {:service (format "[%s] 50_0th_percentile_complete_latency" task-name)
                                            :window "10s"
                                            :period 10
                                            :label "10s 50th percentile complete latency"
                                            :metric :complete-latency
                                            :quantile 0.50
                                            :value (get latencies-vals 0.5)
                                            :tags ["complete_latency_50th" "onyx" "50_percentile" task-name job-name peer-id-str]}))

                       (>!! ch (merge core {:service (format "[%s] 90_0th_percentile_complete_latency" task-name)
                                            :window "10s"
                                            :period 10
                                            :label "10s 90th percentile complete latency"
                                            :quantile 0.90
                                            :metric :complete-latency
                                            :value (get latencies-vals 0.90)
                                            :tags ["complete_latency_90th" "onyx" task-name job-name peer-id-str]}))

                       (>!! ch (merge core {:service (format "[%s] 99_0th_percentile_complete_latency" task-name)
                                            :window "10s"
                                            :period 10
                                            :quantile 0.99
                                            :label "10s 99th percentile complete latency"
                                            :metric :complete-latency
                                            :value (get latencies-vals 0.99)
                                            :tags ["complete_latency_99th" "onyx" task-name job-name peer-id-str]}))

                       (>!! ch (merge core {:service (format "[%s] 99_9th_percentile_complete_latency" task-name)
                                            :window "10s"
                                            :period 10
                                            :quantile 0.999
                                            :label "10s 99.9th percentile complete latency"
                                            :metric :complete-latency
                                            :value (get latencies-vals 0.999)
                                            :tags ["complete_latency_99_9th" "onyx" task-name job-name peer-id-str]}))
                       (>!! ch (merge core {:service (format "[%s] max_complete_latency" task-name)
                                            :window "10s"
                                            :period 10
                                            :quantile 1.0
                                            :label "10s max percentile complete latency"
                                            :metric :complete-latency
                                            :value (get latencies-vals 1.0)
                                            :tags ["complete_latency_max" "onyx" task-name job-name peer-id-str]})))))

                 (let [rate+latency (:rate+latency-10s metrics)
                       latency-snapshot (im/snapshot! rate+latency)
                       latencies-vals (->> latency-snapshot
                                           :latencies
                                           (map (juxt key (fn [kv]
                                                            (when-let [v (val kv)]
                                                              (float (/ v 1000000.0))))))
                                           (into {}))]
                   (>!! ch (merge core {:service (format "[%s] 50th_percentile_batch_latency" task-name)
                                        :window "10s"
                                        :period 10
                                        :quantile 0.50
                                        :label "10s 50th percentile batch latency"
                                        :metric :batch-latency
                                        :value (get latencies-vals 0.5)
                                        :tags ["batch_latency_50th" "onyx" "50_percentile" task-name job-name peer-id-str]}))

                   (>!! ch (merge core {:service (format "[%s] 90th_percentile_batch_latency" task-name)
                                        :window "10s"
                                        :period 10
                                        :label "10s 90th percentile batch latency"
                                        :quantile 0.90
                                        :value (get latencies-vals 0.90)
                                        :metric :batch-latency
                                        :tags ["batch_latency_90th" "onyx" task-name job-name peer-id-str]}))

                   (>!! ch (merge core {:service (format "[%s] 99th_percentile_batch_latency" task-name)
                                        :window "10s"
                                        :period 10
                                        :quantile 0.99
                                        :label "10s 99th percentile batch latency"
                                        :value (get latencies-vals 0.99)
                                        :metric :batch-latency
                                        :tags ["batch_latency_99th" "onyx" task-name job-name peer-id-str]}))

                   (>!! ch (merge core {:service (format "[%s] 99_9th_percentile_batch_latency" task-name)
                                        :window "10s"
                                        :period 10
                                        :quantile 0.999
                                        :label "10s 99.9th percentile batch latency"
                                        :value (get latencies-vals 0.999)
                                        :metric :batch-latency
                                        :tags ["batch_latency_99_9th" "onyx" task-name job-name peer-id-str]}))
                   (>!! ch (merge core {:service (format "[%s] max_batch_latency" task-name)
                                        :window "10s"
                                        :period 10
                                        :quantile 1.0
                                        :label "10s max percentile batch latency"
                                        :value (get latencies-vals 1.0)
                                        :metric :batch-latency
                                        :tags ["batch_latency_max" "onyx" task-name job-name peer-id-str]})))
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
    (let [{:keys [rate batch-start rate+latency-10s completion-tracking pending-size]} (:onyx.metrics.metrics/metrics event)
          timestamp (System/nanoTime)
          latency (- timestamp @batch-start)
          batch (:onyx.core/batch event)
          batch-size (count batch)]
      (when (= (:onyx/type (:onyx.core/task-map event)) :input)
        (let [transducer (map (fn [v] (clojure.lang.MapEntry. (:id v) timestamp)))]
          (swap! completion-tracking into transducer batch))
        (swap! pending-size (fn [s] (+ s batch-size))))
      (im/update! rate batch-size)
      (im/update! rate+latency-10s latency)
      {}))

(defn on-completion [event message-id rets lifecycle]
    (let [{:keys [completion-rate+latencies-10s completion-tracking pending-size]} (:onyx.metrics.metrics/metrics event)]
      (when-let [v (@completion-tracking message-id)]
        (im/update! completion-rate+latencies-10s (- ^long (System/nanoTime) ^long v))
        (swap! completion-tracking dissoc message-id)
        (swap! pending-size dec))))

(defn on-retry [event message-id rets lifecycle]
  (let [{:keys [retry-rate completion-tracking pending-size]} (:onyx.metrics.metrics/metrics event)]
    (im/update! retry-rate 1)
    (swap! completion-tracking dissoc message-id)
    (swap! pending-size dec)))

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
