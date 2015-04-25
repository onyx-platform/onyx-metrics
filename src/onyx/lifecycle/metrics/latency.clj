(ns onyx.lifecycle.metrics.latency
  (:require [rotating-seq.core :as rsc]
            [taoensso.timbre :refer [fatal]]))

(defn pre [event lifecycle]
  (let [retention (:latency/retention-ms lifecycle)
        interval 1000
        r-seq (rsc/create-r-seq retention interval)
        state (or (:onyx.metrics/state event) (atom {}))]
    (swap! state assoc :latency r-seq)
    {:onyx.metrics/state state
     :onyx.metrics.latency/fut
     (future
       (try
         (loop []
           (Thread/sleep interval)
           (swap! state update-in [:latency] rsc/expire-bucket)
           (recur))
         (catch InterruptedException e)
         (catch Throwable e
           (fatal e))))}))

(defn pre-batch [event lifecycle]
  {:onyx.metrics.latency/batch-start (System/currentTimeMillis)})

(defn post-batch [event lifecycle]
  (let [latency (- (System/currentTimeMillis) (:onyx.metrics.latency/batch-start event))
        state (:onyx.metrics/state event)]
    (swap! state update-in [:latency] rsc/add-to-head [latency])
    {}))

(defn post [event lifecycle]
  (future-cancel (:onyx.metrics.latency/fut event))
  {})
