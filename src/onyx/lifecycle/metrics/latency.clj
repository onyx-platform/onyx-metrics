(ns onyx.lifecycle.metrics.latency
  (:require [rotating-seq.core :as rsc]
            [taoensso.timbre :refer [fatal]]))

(defn before [event lifecycle]
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

(defn before-batch [event lifecycle]
  {:onyx.metrics.latency/batch-start (System/currentTimeMillis)})

(defn after-batch [event lifecycle]
  (let [latency (- (System/currentTimeMillis) (:onyx.metrics.latency/batch-start event))
        state (:onyx.metrics/state event)]
    (swap! state update-in [:latency] rsc/add-to-head [latency])
    {}))

(defn after-task [event lifecycle]
  (future-cancel (:onyx.metrics.latency/fut event))
  {})

(def calls
  {:lifecycle/before-task :onyx.lifecycle.metrics.latency/before-task
   :lifecycle/before-batch :onyx.lifecycle.metrics.latency/before-batch
   :lifecycle/after-batch :onyx.lifecycle.metrics.latency/after-batch
   :lifecycle/after-task :onyx.lifecycle.metrics.latency/after-task})
