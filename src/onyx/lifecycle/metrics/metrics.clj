(ns onyx.lifecycle.metrics.metrics
  (:require [interval-metrics.core :as im]
            [taoensso.timbre :refer [fatal info]]))

(defrecord Metrics [rate batch-start rate+latency-10s retry-rate completion-tracking completion-rate+latencies-10s])

(defn build-metrics []
  (let [rate (im/rate)
        retry-rate (im/rate)
        rate+latency-10s (im/rate+latency {:rate-unit :nanoseconds 
                                           :latency-unit :nanoseconds
                                           :quantiles [0.5 0.90 0.95 0.99 0.999]})

        completion-rate+latencies-10s (im/rate+latency {:rate-unit :nanoseconds 
                                                        :latency-unit :nanoseconds
                                                        :quantiles [0.5 0.90 0.95 0.99 0.999]})
        batch-start (atom nil)]
    (->Metrics rate batch-start rate+latency-10s retry-rate (atom {}) completion-rate+latencies-10s))) 

(defn build-before-batch [event-key]
  (fn metrics-before-batch [event lifecycle]
    ;; swap into atom so we only need one event map lookup later
    (reset! (:batch-start (event event-key)) (System/nanoTime))
    {}))

(defn build-after-batch [event-key]
  (fn metrics-after-batch [event lifecycle]
    (let [{:keys [rate batch-start rate+latency-10s completion-tracking]} (event-key event)
          timestamp (System/nanoTime)
          latency (- timestamp @batch-start)
          batch (:onyx.core/batch event)
          map-timings-transducer (map (fn [v] (clojure.lang.MapEntry. (:id v) timestamp)))]
      (swap! completion-tracking into map-timings-transducer batch)
      (im/update! rate (count batch))
      (im/update! rate+latency-10s latency)
      {})))

(defn build-on-completion [event-key]
  (fn [event message-id rets lifecycle]
    (let [metric (event-key event)
          completion-rate+latencies-10s (:completion-rate+latencies-10s metric)
          completion-tracking (:completion-tracking metric)] 
      (when-let [v (@completion-tracking message-id)]
        (im/update! completion-rate+latencies-10s (- ^long (System/nanoTime) ^long v))
        (swap! completion-tracking dissoc message-id)))))

(defn build-on-retry [event-key]
  (fn [event message-id rets lifecycle]
    (let [metric (event-key event)
          retry-rate (:retry-rate metric)] 
      (im/update! retry-rate 1))))
