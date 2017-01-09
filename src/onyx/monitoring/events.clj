(ns onyx.monitoring.events
  (:require [metrics.core :refer [new-registry]]
            [com.stuartsierra.component :as component]
            [taoensso.timbre :refer [warn info]]
            [metrics.meters :as m :refer [meter rates]]
            [metrics.histograms :as h]
            [metrics.timers :as t]
            [metrics.gauges :as g]
            [metrics.counters :as c])
  (:import [com.codahale.metrics JmxReporter]
           [java.util.concurrent TimeUnit]))

(defn job-metric->metric-str [s attribute value]
  (let [[t job task lifecycle] (-> s
                                   (clojure.string/replace #"^name=" "")
                                   (clojure.string/split #"[.]"))
        lifecycle (clojure.string/replace lifecycle #"-" "_")
        tags (format "{job=\"%s\", task=\"%s\",}" job task)]
    (format "onyx_job_task_%s_%s%s %s" lifecycle (name attribute) tags value)))

(defn update-timer! [^com.codahale.metrics.Timer timer ms]
  (.update timer ms TimeUnit/MILLISECONDS))

(defrecord Monitoring [registry]
  component/Lifecycle
  (component/start [component]
    (let [reporter (.build (JmxReporter/forRegistry registry))
          _ (.start ^JmxReporter reporter)] 
      (assoc component :registry registry :reporter reporter)))
  (component/stop [{:keys [registry reporter] :as component}]
    (.stop ^JmxReporter reporter)
    (metrics.core/remove-metrics registry)
    (assoc component :registry nil :reporter nil)))

(defn new-monitoring []
  (let [reg (new-registry)
        write-log-entry-bytes (h/histogram reg ["zookeeper" "write-log-entry" "bytes"])
        write-log-entry-latency (t/timer reg ["zookeeper" "write-log-entry" "latency"])
        write-catalog-bytes (h/histogram reg ["zookeeper" "write-catalog" "bytes"])
        write-catalog-latency (t/timer reg ["zookeeper" "write-catalog" "latency"])
        write-workflow-bytes (h/histogram reg ["zookeeper" "write-workflow" "bytes"])
        write-workflow-latency (t/timer reg ["zookeeper" "write-workflow" "latency"])
        write-flow-conditions-bytes (h/histogram reg ["zookeeper" "write-flow-conditions" "bytes"])
        write-flow-conditions-latency (t/timer reg ["zookeeper" "write-flow-conditions" "latency"])
        write-lifecycles-bytes (h/histogram reg ["zookeeper" "write-lifecycles" "bytes"])
        write-lifecycles-latency (t/timer reg ["zookeeper" "write-lifecycles" "latency"])
        write-task-bytes (h/histogram reg ["zookeeper" "write-task" "bytes"])
        write-task-latency (t/timer reg ["zookeeper" "write-task" "latency"])
        write-chunk-bytes (h/histogram reg ["zookeeper" "write-chunk" "bytes"])
        write-chunk-latency (t/timer reg ["zookeeper" "write-chunk" "latency"])
        write-job-scheduler-bytes (h/histogram reg ["zookeeper" "write-job-scheduler" "bytes"])
        write-job-scheduler-latency (t/timer reg ["zookeeper" "write-job-scheduler" "latency"])
        write-messaging-bytes (h/histogram reg ["zookeeper" "write-messaging" "bytes"])
        write-messaging-latency (t/timer reg ["zookeeper" "write-messaging" "latency"])
        force-write-chunk-bytes (h/histogram reg ["zookeeper" "force-write-chunk" "bytes"])
        force-write-chunk-latency (t/timer reg ["zookeeper" "force-write-chunk" "latency"])
        write-origin-bytes (h/histogram reg ["zookeeper" "write-origin" "bytes"])
        write-origin-latency (t/timer reg ["zookeeper" "write-origin" "latency"])
        read-log-entry-latency (t/timer reg ["zookeeper" "read-log-entry" "latency"])
        read-catalog-latency (t/timer reg ["zookeeper" "read-catalog" "latency"])
        read-workflow-latency (t/timer reg ["zookeeper" "read-workflow" "latency"])
        read-flow-conditions-latency (t/timer reg ["zookeeper" "read-flow-conditions" "latency"])
        read-lifecycles-latency (t/timer reg ["zookeeper" "read-lifecycles" "latency"])
        read-task-latency (t/timer reg ["zookeeper" "read-task" "latency"])
        read-chunk-latency (t/timer reg ["zookeeper" "read-chunk" "latency"])
        read-job-scheduler-latency (t/timer reg ["zookeeper" "read-job-scheduler" "latency"])
        read-messaging-latency (t/timer reg ["zookeeper" "read-messaging" "latency"])
        force-read-chunk-latency (t/timer reg ["zookeeper" "force-read-chunk" "latency"])
        read-origin-latency (t/timer reg ["zookeeper" "read-origin" "latency"])
        gc-log-entry-position (g/gauge reg ["zookeeper" "gc-log-entry" "position"])
        gc-log-entry-latency (t/timer reg ["zookeeper" "gc-log-entry" "latency"])
        group-prepare-join-cnt (c/counter reg ["group" "prepare-join" "event"])
        group-accept-join-cnt (c/counter reg ["group" "accept-join" "event"])
        group-notify-join-cnt (c/counter reg ["group" "notify-join" "event"])]
    (map->Monitoring {:monitoring :custom
                      :registry reg
                      :zookeeper-write-log-entry (fn [config metric] 
                                                   (h/update! write-log-entry-bytes (:bytes metric))
                                                   (update-timer! write-log-entry-latency (:latency metric)))
                      :zookeeper-write-catalog (fn [config metric] 
                                                 (h/update! write-catalog-bytes (:bytes metric))
                                                 (update-timer! write-catalog-latency (:latency metric)))
                      :zookeeper-write-workflow (fn [config metric] 
                                                  (h/update! write-workflow-bytes (:bytes metric))
                                                  (update-timer! write-workflow-latency (:latency metric)))
                      :zookeeper-write-flow-conditions (fn [config metric] 
                                                         (h/update! write-flow-conditions-bytes (:bytes metric))
                                                         (update-timer! write-flow-conditions-latency (:latency metric)))
                      :zookeeper-write-lifecycles (fn [config metric] 
                                                    (h/update! write-lifecycles-bytes (:bytes metric))
                                                    (update-timer! write-lifecycles-latency (:latency metric)))
                      :zookeeper-write-task (fn [config metric] 
                                              (h/update! write-task-bytes (:bytes metric))
                                              (update-timer! write-task-latency (:latency metric)))
                      :zookeeper-write-chunk (fn [config metric] 
                                               (h/update! write-task-bytes (:bytes metric))
                                               (update-timer! write-task-latency (:latency metric)))
                      :zookeeper-write-job-scheduler (fn [config metric] 
                                                       (h/update! write-job-scheduler-bytes (:bytes metric))
                                                       (update-timer! write-job-scheduler-latency (:latency metric)))
                      :zookeeper-write-messaging (fn [config metric] 
                                                   (h/update! write-messaging-bytes (:bytes metric))
                                                   (update-timer! write-messaging-latency (:latency metric)))
                      :zookeeper-force-write-chunk (fn [config metric] 
                                                     (h/update! force-write-chunk-bytes (:bytes metric))
                                                     (update-timer! force-write-chunk-latency (:latency metric)))
                      :zookeeper-write-origin (fn [config metric] 
                                                (h/update! write-origin-bytes (:bytes metric))
                                                (update-timer! write-origin-latency (:latency metric)))
                      :zookeeper-read-log-entry (fn [config metric] 
                                                  (update-timer! read-log-entry-latency (:latency metric)))
                      :zookeeper-read-catalog (fn [config metric] 
                                                (update-timer! read-catalog-latency (:latency metric)))
                      :zookeeper-read-workflow (fn [config metric] 
                                                 (update-timer! read-workflow-latency (:latency metric)))
                      :zookeeper-read-flow-conditions (fn [config metric] 
                                                        (update-timer! read-flow-conditions-latency (:latency metric)))
                      :zookeeper-read-lifecycles (fn [config metric] 
                                                   (update-timer! read-lifecycles-latency (:latency metric)))
                      :zookeeper-read-task (fn [config metric] 
                                             (update-timer! read-task-latency (:latency metric)))
                      :zookeeper-read-chunk (fn [config metric] 
                                              (update-timer! read-chunk-latency (:latency metric)))
                      :zookeeper-read-origin (fn [config metric] 
                                               (update-timer! read-origin-latency (:latency metric)))
                      :zookeeper-read-job-scheduler (fn [config metric] 
                                                      (update-timer! read-job-scheduler-latency (:latency metric)))
                      :zookeeper-read-messaging (fn [config metric] 
                                                  (update-timer! read-messaging-latency (:latency metric)))
                      :zookeeper-gc-log-entry (fn [config metric] 
                                                (h/update! gc-log-entry-position (:position metric))
                                                (update-timer! gc-log-entry-latency (:latency metric)))
                      :group-prepare-join (fn [config metric] 
                                            (c/inc! group-prepare-join-cnt))
                      :group-notify-join (fn [config metric] 
                                           (c/inc! group-notify-join-cnt))
                      :group-accept-join (fn [config metric] 
                                           (c/inc! group-accept-join-cnt))})))
