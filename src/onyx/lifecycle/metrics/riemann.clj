(ns onyx.lifecycle.metrics.riemann
  (:require [clojure.core.async :refer [chan >!! <!!]]
            [riemann.client :as r]
            [taoensso.timbre :refer [warn fatal]]
            [onyx.lifecycle.metrics.common :refer [quantile]]))

(defn start-riemann-sender [address port ch]
  (future
    (let [client (r/tcp-client {:host address :port port})]
      (loop []
        (try
          (when-let [event (<!! ch)]
            @(r/send-event client event))
          (catch InterruptedException e
            ;; Intentionally pass.
            )
          (catch Throwable e
            (warn e)))
        (recur)))))

(defn before-task [event {:keys [riemann/address riemann/port] :as lifecycle}]
  (let [ch (chan (or (:riemann/buffer-capacity lifecycle) 10000))]
    {:onyx.metrics/sender-thread (start-riemann-sender address port ch)
     :onyx.metrics/riemann-fut
     (future
       (try
         (loop []
           (Thread/sleep (:riemann/interval-ms lifecycle))
           (let [state @(:onyx.metrics/state event)
                 name (str (:riemann/workflow-name lifecycle))
                 task-name (str (:onyx.core/task event))]
             (assert task-name ":riemann/workflow-name must be defined")
             (when-let [throughput (:throughput state)]
               (>!! ch {:service (format "[%s] 1s_throughput" task-name)
                        :state "ok" :metric (apply + (map #(apply + %) (take 1 throughput)))
                        :tags ["throughput_1s" "onyx" task-name name]})

               (>!! ch {:service (format "[%s] 10s_throughput" task-name)
                        :state "ok" :metric (apply + (map #(apply + %) (take 10 throughput)))
                        :tags ["throughput_10s" "onyx" task-name name]})

               (>!! ch {:service (format "[%s] 60s_throughput" task-name)
                        :state "ok" :metric (apply + (map #(apply + %) (take 60 throughput)))
                        :tags ["throughput_60s" "onyx" task-name name]}))
             (when-let [latency (:latency state)]
               (>!! ch {:service (format "[%s] 50_percentile_latency" task-name)
                        :state "ok" :metric (quantile 0.50 (apply concat (take 10 latency)))
                        :tags ["latency_50th" "onyx" "50_percentile" task-name name]})

               (>!! ch {:service (format "[%s] 90_percentile_latency" task-name)
                        :state "ok" :metric (quantile 0.90 (apply concat (take 10 latency)))
                        :tags ["latency_90th" "onyx" task-name name]})

               (>!! ch {:service (format "[%s] 99_percentile_latency" task-name)
                        :state "ok" :metric (quantile 0.99 (apply concat (take 10 latency)))
                        :tags ["latency_99th" "onyx" task-name name]}))
             (recur)))
         (catch InterruptedException e)
         (catch Throwable e
           (fatal e))))}))

(defn after-task [event lifecycle]
  (future-cancel (:onyx.metrics/riemann-fut event))
  (future-cancel (:onyx.metrics/sender-thread event))
  {})

(def calls
  {:lifecycle/before-task-start before-task
   :lifecycle/after-task-stop after-task})
