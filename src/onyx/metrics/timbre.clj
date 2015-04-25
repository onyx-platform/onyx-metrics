(ns onyx.metrics.timbre
  (:require [taoensso.timbre :as timbre]))

(defn pre [event lifecycle]
  {:onyx.metrics/timbre-fut
   (future
     (try
       (loop []
         (Thread/sleep (:onyx.metrics.throughput/interval-ms lifecycle))
         (let [throughput (:throughput @(:onyx.metrics/state event))]
           (taoensso.timbre/info (format "[%s] Task [%s] :: Throughput :: %s segments in 10s :: %s segments in 30s :: %s segments in 60s"
                                         (:onyx.core/id event) (:onyx.core/task-id event)
                                         (apply + (map #(apply + %) (take 10 throughput)))
                                         (apply + (map #(apply + %) (take 30 throughput)))
                                         (apply + (map #(apply + %) (take 60 throughput)))))
           (recur)))
       (catch InterruptedException e)
       (catch Throwable e
         (timbre/fatal e))))})

(defn post [event lifecycle]
  (future-cancel (:onyx.metrics/timbre-fut event))
  {})
