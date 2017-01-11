(ns onyx.lifecycle.metrics.metrics
  (:require [taoensso.timbre :refer [info warn fatal]]
            [metrics.core :refer [new-registry]]
            [clojure.set :refer [rename-keys]]
            [onyx.static.util :refer [kw->fn]]
            [metrics.timers :as t]
            [metrics.meters :as m])
  (:import [java.util.concurrent TimeUnit]))

(defn update-timer! [^com.codahale.metrics.Timer timer ms]
  (.update timer ms TimeUnit/MILLISECONDS))

(defn before-task [event lifecycle]
  (when (:metrics/workflow-name lifecycle)
    (throw (ex-info ":metrics/workflow-name has been deprecated. Use job metadata such as:
                    {:workflow ...
                    :lifecycles ...
                    :metadata {:name \"YOURJOBNAME\"}}
                    to supply your job's name" {})))
  (let [job-id (:onyx.core/job-id event)
        job-name (str (get-in event [:onyx.core/task-information :metadata :name] job-id))
        task-name (name (:onyx.core/task event))
        peer-id (:onyx.core/id event)
        peer-id-str (str peer-id)
        monitoring (:onyx.core/monitoring event)
        reg (:registry monitoring)
        batch-latency (t/timer reg [job-name task-name "batch-latency"])
        throughput (m/meter reg [job-name task-name "throughput"])
        retry-latency (t/timer reg [job-name task-name "retry-segment"]) 
        ack-latency (t/timer reg [job-name task-name "ack-segment"])]
    {:onyx.core/monitoring (merge monitoring
                                  {:peer-batch-latency (fn [config metric]
                                                         (update-timer! batch-latency (:latency metric)))
                                   :peer-retry-segment (fn [config metric]
                                                         (update-timer! retry-latency (:latency metric)))
                                   :peer-ack-segment (fn [config metric]
                                                       (update-timer! ack-latency (:latency metric)))
                                   :peer-ack-segments (fn [config metric]
                                                        (update-timer! ack-latency (:latency metric)))
                                   :peer-processed-segments (fn [config metric]
                                                              (m/mark! throughput (:count metric)))})}))

(def calls
  {:lifecycle/before-task-start before-task})

