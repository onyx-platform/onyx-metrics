(ns onyx.lifecycle.metrics.metrics
  (:require [taoensso.timbre :refer [info warn fatal]]
            [metrics.core :refer [new-registry]]
            [clojure.set :refer [rename-keys]]
            [onyx.static.util :refer [kw->fn]]
            [onyx.protocol.task-state :as task]
            [metrics.counters :as c]
            [metrics.timers :as t]
            [metrics.gauges :as g]
            [metrics.meters :as m])
  (:import [java.util.concurrent TimeUnit]))

(defn new-lifecycle-latency [reg tag lifecycle]
  (let [timer ^com.codahale.metrics.Timer (t/timer reg (into tag ["task-lifecycle" (name lifecycle)]))] 
    (fn [state latency-ns]
      (.update timer latency-ns TimeUnit/NANOSECONDS))))

(defn new-read-batch [reg tag lifecycle]
  (let [throughput (m/meter reg (into tag ["task-lifecycle" (name lifecycle) "throughput"]))
        timer ^com.codahale.metrics.Timer (t/timer reg (into tag ["task-lifecycle" (name lifecycle)]))] 
    (fn [state latency-ns]
      (m/mark! throughput (count (:onyx.core/batch (task/get-event state))))
      (.update timer latency-ns TimeUnit/NANOSECONDS))))

(defn new-write-batch [reg tag lifecycle]
  (let [throughput (m/meter reg (into tag ["task-lifecycle" (name lifecycle) "throughput"]))
        timer ^com.codahale.metrics.Timer 
        (t/timer reg (into tag ["task-lifecycle" (name lifecycle)]))] 
    (fn [state latency-ns]
      ;; TODO, for blockable lifecycles, keep adding latencies until advance?
      (.update timer latency-ns TimeUnit/NANOSECONDS)
      (when (task/advanced? state)
        (m/mark! throughput (reduce (fn [c {:keys [leaves]}]
                                      (+ c (count leaves)))
                                    0
                                    (:tree (:onyx.core/results (task/get-event state)))))))))

(defn update-rv-epoch [cnt-replica-version cnt-epoch epoch-rate]
  (fn [state latency-ns]
    (m/mark! epoch-rate 1)
    (c/inc! cnt-replica-version (- (task/replica-version state) 
                                   (c/value cnt-replica-version)))
    (c/inc! cnt-epoch (- (task/epoch state) (c/value cnt-epoch)))))

;; FIXME, close counters and remove them from the registry
(defn before-task [{:keys [onyx.core/job-id onyx.core/id onyx.core/monitoring
                           onyx.core/task] :as event} 
                   lifecycle] 
  (when (:metrics/workflow-name lifecycle)
    (throw (ex-info ":metrics/workflow-name has been deprecated. Use job metadata such as:
                     {:workflow ...
                      :lifecycles ...
                      :metadata {:name \"YOURJOBNAME\"}}
                      to supply your job's name" {})))
  (let [lifecycles (or (:metrics/lifecycles lifecycle) 
                       #{:lifecycle/read-batch :lifecycle/write-batch 
                         :lifecycle/apply-fn :lifecycle/unblock-subscribers})
        job-name (str (get-in event [:onyx.core/task-information :metadata :name] job-id))
        task-name (name (:onyx.core/task event))
        reg (:registry monitoring)
        _ (when-not reg (throw (Exception. "Monitoring component is not setup")))
        tag ["job" job-name "task" task-name "peer-id" (str id)]
        cnt-replica-version (c/counter reg (conj tag "replica-version"))
        cnt-epoch (c/counter reg (conj tag "epoch"))
        epoch-rate (m/meter reg  (conj tag "epoch-rate"))
        update-rv-epoch-fn (update-rv-epoch cnt-replica-version cnt-epoch epoch-rate)]
    {:onyx.core/monitoring (reduce 
                            (fn [mon lifecycle]
                              (assoc mon 
                                     lifecycle 
                                     (case lifecycle
                                       :lifecycle/unblock-subscribers update-rv-epoch-fn
                                       :lifecycle/read-batch (new-read-batch reg tag :lifecycle/read-batch) 
                                       :lifecycle/write-batch (new-write-batch reg tag :lifecycle/write-batch) 
                                       (new-lifecycle-latency reg tag lifecycle))))
                            monitoring
                            lifecycles)}))

(def calls
  {:lifecycle/before-task-start before-task})

