(ns onyx.metrics.throughput
  (:require [rotating-seq.core :as rsc]
            [taoensso.timbre :refer [fatal]]))

(defn pre [event lifecycle]
  (let [retention (:onyx.metrics.throughput/retention-ms lifecycle)
        interval (:onyx.metrics.throughput/interval-ms lifecycle)
        r-seq {:throughput (rsc/create-r-seq retention interval)}
        state (or (:onyx.metrics/state event) (atom {}))]
    {:onyx.metrics/state (swap! state assoc r-seq)
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

[{:lifecycle/task :all
  :lifecycle/pre :onyx.metrics.throughput/pre
  :lifecycle/post-batch :onyx.metrics.throughput/post-batch
  :lifecycle/post :onyx.metrics.throughput/post
  :onyx.metrics.throughput/retention-ms 60000
  :onyx.metrics.throughput/interval-ms 1000
  :lifecycle/doc "Instruments a tasks throughput metrics"}

 {:lifecycle/task :all
  :lifecycle/pre :onyx.metrics.timbre/pre
  :lifecycle/post :onyx.metrics.timbre/post
  :onyx.metrics.throughput/interval-ms 5000
  :lifecycle/doc "Prints task metrics to Timbre every 5000 ms"}]
