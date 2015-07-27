(ns onyx.lifecycle.metrics.riemann
  (:require [riemann.client :as r]
            [taoensso.timbre :as timbre]
            [onyx.lifecycle.metrics.common :refer [quantile]]))

(defn sayhi [x]
  (identity x))

(defn before-task [event lifecycle]
  {:onyx.metrics/riemann-fut
   (future
     (try
       (loop []
         (Thread/sleep (:riemann/interval-ms lifecycle))
         (let [state @(:onyx.metrics/state event)
               client (r/tcp-client {:host (:riemann/client lifecycle)})
               name   (:riemann/name lifecycle)
               task-name (str (:onyx.core/task event))]
           (when-let [throughput (:throughput state)]
             (r/send-event client {:service (format "[%s] 1s_throughput" task-name)
                                   :state "ok" :metric (apply + (map #(apply + %) (take 1 throughput)))
                                   :tags ["throughput_1s" "onyx" task-name name]})

             (r/send-event client {:service (format "[%s] 10s_throughput" task-name)
                                   :state "ok" :metric (apply + (map #(apply + %) (take 10 throughput)))
                                   :tags ["throughput_10s" "onyx" task-name name]})

             (r/send-event client {:service (format "[%s] 60s_throughput" task-name)
                                   :state "ok" :metric (apply + (map #(apply + %) (take 60 throughput)))
                                   :tags ["throughput_60s" "onyx" task-name name]}))
           (when-let [latency (:latency state)]
             (r/send-event client {:service (format "[%s] 50_percentile_latency" task-name)
                                   :state "ok" :metric (quantile 0.50 (apply concat (take 10 latency)))
                                   :tags ["latency_50th" "onyx" "50_percentile" task-name name]})

             (r/send-event client {:service (format "[%s] 90_percentile_latency" task-name)
                                   :state "ok" :metric (quantile 0.90 (apply concat (take 10 latency)))
                                   :tags ["latency_90th" "onyx" task-name name]})

             (r/send-event client {:service (format "[%s] 99_percentile_latency" task-name)
                                   :state "ok" :metric (quantile 0.99 (apply concat (take 10 latency)))
                                   :tags ["latency_99th" "onyx" task-name name]}))
           (recur)))
       (catch InterruptedException e)
       (catch Throwable e
         (timbre/fatal e))))})

(defn after-task [event lifecycle]
  (future-cancel (:onyx.metrics/riemann-fut event))
  {})

(def calls
  {:lifecycle/before-task-start before-task
   :lifecycle/after-task-stop after-task})
