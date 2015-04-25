(ns onyx.metrics.throughput
  (:require [rotating-seq.core :as rsc]
            [taoensso.timbre :refer [fatal]]))

(defn pre [event lifecycle]
  (let [retention (:onyx.metrics.throughput/retention-ms lifecycle)
        interval (:onyx.metrics.throughput/interval-ms lifecycle)
        r-seq {:throughput (rsc/create-r-seq retention interval)}
        state (or (:onyx.metrics/state event) (atom {}))]
    (swap! state assoc :throughput r-seq)
    {:onyx.metrics/state state
     :onyx.metrics/fut (future
                         (try
                           (loop []
                             (Thread/sleep interval)
                             (swap! state update-in [:throughput] rsc/expire-bucket)
                             (recur))
                           (catch Throwable e
                             (fatal e))))}))

(defn post-batch [event lifecycle]
  (let [state (:onyx.metrics/state event)]
    (swap! state update-in [:throughput] rsc/add-to-head [(count (:onyx.core/batch event))])
    {}))

(defn post [event lifecycle]
  (future-cancel (:onyx.metrics/fut event))
  {})
