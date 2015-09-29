(ns onyx.lifecycle.metrics.timbre
  (:require [taoensso.timbre :as timbre]
            [onyx.lifecycle.metrics.metrics :as metrics]
            [interval-metrics.core :as im]))

(def historical-throughput-max-count 1000)
(def latency-period 10)

(defn before-task [event lifecycle]
  (let [throughputs (atom (list))
        metrics (metrics/build-metrics)] 
    {:onyx.metrics.timbre/metrics metrics
     :onyx.metrics.timbre/timbre-fut
     (future
       (try
         (loop [cycle-count 0]
           (Thread/sleep 1000)
           (let [throughput (im/snapshot! (:rate metrics))
                 throughputs-val (swap! throughputs (fn [tps]
                                                      (conj (take (dec historical-throughput-max-count) 
                                                                  tps)
                                                            throughput)))] 
             (taoensso.timbre/info (format "[%s] Task [%s] :: Throughput 1s :: %s segments"
                                           (:onyx.core/id event) (:onyx.core/task-id event)
                                           (apply + (take 1 throughputs-val))))
             (taoensso.timbre/info (format "[%s] Task [%s] :: Throughput 10s :: %s segments"
                                           (:onyx.core/id event) (:onyx.core/task-id event)
                                           (apply + (take 10 throughputs-val))))
             (taoensso.timbre/info (format "[%s] Task [%s] :: Throughput 30s :: %s segments"
                                           (:onyx.core/id event) (:onyx.core/task-id event)
                                           (apply + (take 30 throughputs-val))))
             (taoensso.timbre/info (format "[%s] Task [%s] :: Throughput 60s :: %s segments"
                                           (:onyx.core/id event) (:onyx.core/task-id event)
                                           (apply + (take 60 throughputs-val)))))

           (let [retry-rate-val (im/snapshot! (:retry-rate metrics))]
             (taoensso.timbre/info (format "[%s] Task [%s] :: Retries 1s :: %s segments"
                                           (:onyx.core/id event) (:onyx.core/task-id event)
                                           retry-rate-val)))
           
           (when (= cycle-count 0)
             (when (= :input (:onyx/type (:onyx.core/task-map event)))
               (let [completion-rate+latencies-10s (:completion-rate+latencies-10s metrics)
                     completion-rate-snapshot (im/snapshot! completion-rate+latencies-10s)
                     latencies-vals (->> completion-rate-snapshot 
                                         :latencies
                                         (map (juxt key (fn [kv] 
                                                          (when-let [v (val kv)]
                                                            (float (/ v 1000000.0))))))
                                         (into {}))]
                 (taoensso.timbre/info (format "[%s] Task [%s] :: Completion Latency 10s 50th Percentile :: %s ms"
                                               (:onyx.core/id event) (:onyx.core/task-id event)
                                               (get latencies-vals 0.5)))
                 (taoensso.timbre/info (format "[%s] Task [%s] :: Completion Latency 10s 90th Percentile :: %s ms"
                                               (:onyx.core/id event) (:onyx.core/task-id event)
                                               (get latencies-vals 0.9)))
                 (taoensso.timbre/info (format "[%s] Task [%s] :: Completion Latency 10s 99th Percentile :: %s ms"
                                               (:onyx.core/id event) (:onyx.core/task-id event)
                                               (get latencies-vals 0.99)))
                 (taoensso.timbre/info (format "[%s] Task [%s] :: Completion Latency 10s 99.9th Percentile :: %s ms"
                                               (:onyx.core/id event) (:onyx.core/task-id event)
                                               (get latencies-vals 0.999)))))

             (let [rate+latency (:rate+latency-10s metrics)
                   latency-snapshot (im/snapshot! rate+latency) 
                   latencies-vals (->> latency-snapshot 
                                       :latencies
                                       (map (juxt key (fn [kv] 
                                                        (float (/ (val kv) 1000000.0)))))
                                       (into {}))]
               (taoensso.timbre/info (format "[%s] Task [%s] :: Batch Latency 10s 50th Percentile :: %s ms"
                                             (:onyx.core/id event) (:onyx.core/task-id event)
                                             (get latencies-vals 0.5)))
               (taoensso.timbre/info (format "[%s] Task [%s] :: Batch Latency 10s 90th Percentile :: %s ms"
                                             (:onyx.core/id event) (:onyx.core/task-id event)
                                             (get latencies-vals 0.9)))
               (taoensso.timbre/info (format "[%s] Task [%s] :: Batch Latency 10s 99th Percentile :: %s ms"
                                             (:onyx.core/id event) (:onyx.core/task-id event)
                                             (get latencies-vals 0.99)))
               (taoensso.timbre/info (format "[%s] Task [%s] :: Batch Latency 10s 99.9th Percentile :: %s ms"
                                             (:onyx.core/id event) (:onyx.core/task-id event)
                                             (get latencies-vals 0.999)))))
           (recur (mod (inc cycle-count) latency-period)))
         (catch InterruptedException e)
         (catch Throwable e
           (timbre/fatal e))))}))

(defn after-task [event lifecycle]
  (future-cancel (:onyx.metrics.timbre/timbre-fut event))
  {})

(def calls
  {:lifecycle/before-task-start before-task
   :lifecycle/after-ack-segment (metrics/build-on-completion :onyx.metrics.timbre/metrics)
   :lifecycle/after-retry-segment (metrics/build-on-retry :onyx.metrics.timbre/metrics) 
   :lifecycle/before-batch (metrics/build-before-batch :onyx.metrics.timbre/metrics)
   :lifecycle/after-batch (metrics/build-after-batch :onyx.metrics.timbre/metrics)
   :lifecycle/after-task-stop after-task})
