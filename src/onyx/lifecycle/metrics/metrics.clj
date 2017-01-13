(ns onyx.lifecycle.metrics.metrics
  (:require [taoensso.timbre :refer [info warn fatal]]
            [metrics.core :refer [new-registry]]
            [clojure.set :refer [rename-keys]]
            [onyx.static.util :refer [kw->fn]]
            [metrics.timers :as t]
            [metrics.meters :as m])
  (:import [java.util.concurrent TimeUnit]
           [com.codahale.metrics JmxReporter]))

(defn update-timer! [^com.codahale.metrics.Timer timer ms]
  (.update timer ms TimeUnit/MILLISECONDS))

(defn before-task [event lifecycle]
  (when (:metrics/workflow-name lifecycle)
    (throw (ex-info ":metrics/workflow-name has been deprecated. Use job metadata such as:
                    {:workflow ...
                     :lifecycles ...
                     :metadata {:name \"YOURJOBNAME\"}}
                     to supply your job's name" 
                    {})))
  (let [job-id (:onyx.core/job-id event)
        job-name (str (get-in event [:onyx.core/task-information :metadata :name] job-id))
        task-name (name (:onyx.core/task event))
        peer-id (:onyx.core/id event)
        peer-id-str (str peer-id)
        monitoring (:onyx.core/monitoring event)
        task-registry (new-registry)
        names {:peer-batch-latency [job-name task-name "batch-latency"]
               :peer-processed-segments [job-name task-name "throughput"]
               :peer-retry-segment [job-name task-name "retry-segment"]
               :peer-complete-segment [job-name task-name "complete-segment"]
               :peer-ack-segment [job-name task-name "ack-segment"]}
        batch-latency (t/timer task-registry (:peer-batch-latency names))
        throughput (m/meter task-registry (:peer-processed-segments names))
        retry-latency (t/timer task-registry (:peer-retry-segment names)) 
        complete-latency (t/timer task-registry (:peer-complete-segment names))
        ack-latency (t/timer task-registry (:peer-ack-segment names))
        reporter (.build (JmxReporter/forRegistry task-registry))
        _ (.start ^JmxReporter reporter)]
    {:onyx.core/monitoring (merge monitoring
                                  {:task-registry task-registry
                                   :names names
                                   :reporter reporter
                                   :peer-batch-latency (fn [config metric]
                                                         (update-timer! batch-latency (:latency metric)))
                                   :peer-retry-segment (fn [config metric]
                                                         (update-timer! retry-latency (:latency metric)))
                                   :peer-complete-segment (fn [config metric]
                                                            (update-timer! complete-latency (:latency metric)))
                                   :peer-ack-segment (fn [config metric]
                                                       (update-timer! ack-latency (:latency metric)))
                                   :peer-ack-segments (fn [config metric]
                                                        (update-timer! ack-latency (:latency metric)))
                                   :peer-processed-segments (fn [config metric]
                                                              (m/mark! throughput (:count metric)))})}))

(defn after-task [{:keys [onyx.core/monitoring]} lifecycle]
  (-> monitoring :task-registry metrics.core/remove-all-metrics) 
  (.stop ^JmxReporter (:reporter monitoring)) 
  {})


(def calls
  {:lifecycle/before-task-start before-task
   :lifecycle/after-task-stop after-task})

