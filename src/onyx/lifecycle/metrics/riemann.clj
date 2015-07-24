(ns onyx.lifecycle.metrics.riemann
  (:require [riemann.client :as r]
            [onyx.lifecycle.metrics.common :refer [quantile]]))

(defn before-task [event lifecycle]
  {:onyx.metrics/riemann-fut
   (future
     (try
       (loop []
         (Thread/sleep (:riemann/interval-ms lifecycle))
         (let [state @(:onyx.metrics/state event)
               client (r/tcp-client {:host (:onyx.metrics/riemann-client event)})
               name   (:onyx.metrics/riemann-name event)
               task-name (str (:onyx.core/task event))]
           (when-let [throughput (:throughput state)]
             (r/send-event client {:service (format "[%s][%s] 1s_throughput" name task-name)
                                   :state "ok" :metric (apply + (map #(apply + %) (take 1 throughput)))
                                   :tags ["throughput" "onyx" task-name name]})

             (r/send-event client {:service (format "[%s][%s] 10s_throughput" name task-name)
                                   :state "ok" :metric (apply + (map #(apply + %) (take 10 throughput)))
                                   :tags ["throughput_10s" "onyx" task-name name]})

             (r/send-event client {:service (format "[%s][%s] 60s_throughput" name task-name)
                                   :state "ok" :metric (apply + (map #(apply + %) (take 60 throughput)))
                                   :tags ["throughput_60s" "onyx" task-name name]}))
           (when-let [latency (:latency state)]
             (r/send-event client {:service (format "[%s][%s] 10s_latency_50_percentile %s" name task-name)
                                   :state "ok" :metric (quantile 0.50 (apply concat (take 10 latency)))
                                   :tags ["latency" "onyx" "50_percentile" task-name name]})

             (r/send-event client {:service (format "[%s][%s] 10s_latency_90_percentile %s" name task-name)
                                   :state "ok" :metric (quantile 0.90 (apply concat (take 10 latency)))
                                   :tags ["latency" "onyx" task-name name]})

             (r/send-event client {:service (format "[%s][%s] 10s_latency_99_percentile %s" name task-name)
                                   :state "ok" :metric (quantile 0.99 (apply concat (take 10 latency)))
                                   :tags ["latency" "onyx" task-name name]}))))))})
