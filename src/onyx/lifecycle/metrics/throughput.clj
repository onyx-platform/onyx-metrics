(ns onyx.lifecycle.metrics.throughput
  (:require [rotating-seq.core :as rsc]
            [taoensso.timbre :refer [fatal]]))

(defn before-task [event lifecycle]
  (let [retention (:throughput/retention-ms lifecycle)
        interval 1000
        r-seq (rsc/create-r-seq retention interval)
        state (or (:onyx.metrics/state event) (atom {}))]
    (swap! state assoc :throughput r-seq)
    {:onyx.metrics/state state
     :onyx.metrics.throughput/fut
     (future
       (try
         (loop []
           (Thread/sleep interval)
           (swap! state update-in [:throughput] rsc/expire-bucket)
           (recur))
         (catch InterruptedException e)
         (catch Throwable e
           (fatal e))))}))

(defn after-batch [event lifecycle]
  (let [state (:onyx.metrics/state event)]
    (swap! state update-in [:throughput] rsc/update-head
           (fn [coll] (+ (apply + coll) (count (:onyx.core/batch event)))))
    {}))

(defn after-task [event lifecycle]
  (future-cancel (:onyx.metrics.throughput/fut event))
  {})

(def calls
  {:lifecycle/before-task before-task
   :lifecycle/after-batch after-batch
   :lifecycle/after-task after-task})
