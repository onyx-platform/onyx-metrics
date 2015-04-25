(ns onyx.metrics.timbre
  (:require [taoensso.timbre :as timbre]))

(defn pre [event lifecycle]
  {:onyx.metrics/timbre-fut
   (future
     (try
       (loop []
         (Thread/sleep (:onyx.metrics.throughput/interval-ms lifecycle))
         (taoensso.timbre/info (str "Metrics: " @(:onyx.metrics/state event)))
         (recur))
       (catch Throwable e
         (timbre/fatal e))))})

(defn post [event lifecycle]
  (future-cancel (:onyx.metrics/timbre-fut event))
  {})
