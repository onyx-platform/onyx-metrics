(ns onyx.monitoring.events
  (:require [clojure.core.async :refer [chan >!! <!! sliding-buffer alts!! timeout go-loop thread]]
            [taoensso.timbre :refer [warn info]]
            [interval-metrics.core :as im]
            [riemann.client :as r]))

(defn zookeeper-write-log-entry [host-id ch config {:keys [latency bytes]}]
  (>!! ch {:service "zookeeper.write-log-entry.latency"
           :state "ok"
           :tags ["monitoring-config" host-id]
           :metric latency})
  (>!! ch {:service "zookeeper.write-log-entry.bytes"
           :state "ok"
           :tags ["monitoring-config" host-id]
           :metric bytes}))

(defn zookeeper-read-log-entry [host-id ch config {:keys [latency bytes]}]
  (>!! ch {:service "zookeeper.read-log-entry.latency"
           :state "ok"
           :tags ["monitoring-config" host-id]
           :metric latency}))

(defn zookeeper-write-catalog [host-id ch config {:keys [latency bytes]}]
  (>!! ch {:service "zookeeper.write-catalog.latency"
           :state "ok"
           :tags ["monitoring-config" host-id]
           :metric latency})
  (>!! ch {:service "zookeeper.write-catalog.bytes"
           :state "ok"
           :tags ["monitoring-config" host-id]
           :metric bytes}))

(defn zookeeper-write-workflow [host-id ch config {:keys [latency bytes]}]
  (>!! ch {:service "zookeeper.write-workflow.latency"
           :state "ok"
           :tags ["monitoring-config" host-id]
           :metric latency})
  (>!! ch {:service "zookeeper.write-workflow.bytes"
           :state "ok"
           :tags ["monitoring-config" host-id]
           :metric bytes}))

(defn zookeeper-write-flow-conditions [host-id ch config {:keys [latency bytes]}]
  (>!! ch {:service "zookeeper.write-flow-conditions.latency"
           :state "ok"
           :tags ["monitoring-config" host-id]
           :metric latency})
  (>!! ch {:service "zookeeper.write-flow-conditions.bytes"
           :state "ok"
           :tags ["monitoring-config" host-id]
           :metric bytes}))

(defn zookeeper-write-lifecycles [host-id ch config {:keys [latency bytes]}]
  (>!! ch {:service "zookeeper.write-lifecycles.latency"
           :state "ok"
           :tags ["monitoring-config" host-id]
           :metric latency})
  (>!! ch {:service "zookeeper.write-lifecycles.bytes"
           :state "ok"
           :tags ["monitoring-config" host-id]
           :metric bytes}))

(defn zookeeper-write-task [host-id ch config {:keys [latency bytes]}]
  (>!! ch {:service "zookeeper.write-task.latency"
           :state "ok"
           :tags ["monitoring-config" host-id]
           :metric latency})
  (>!! ch {:service "zookeeper.write-task.bytes"
           :state "ok"
           :tags ["monitoring-config" host-id]
           :metric bytes}))

(defn zookeeper-write-chunk [host-id ch config {:keys [latency bytes]}]
  (>!! ch {:service "zookeeper.write-chunk.latency"
           :state "ok"
           :tags ["monitoring-config" host-id]
           :metric latency})
  (>!! ch {:service "zookeeper.write-chunk.bytes"
           :state "ok"
           :tags ["monitoring-config" host-id]
           :metric bytes}))

(defn zookeeper-write-job-scheduler [host-id ch config {:keys [latency bytes]}]
  (>!! ch {:service "zookeeper.write-job-scheduler.latency"
           :state "ok"
           :tags ["monitoring-config" host-id]
           :metric latency})
  (>!! ch {:service "zookeeper.write-job-scheduler.bytes"
           :state "ok"
           :tags ["monitoring-config" host-id]
           :metric bytes}))

(defn zookeeper-write-messaging [host-id ch config {:keys [latency bytes]}]
  (>!! ch {:service "zookeeper.write-messaging.latency"
           :state "ok"
           :tags ["monitoring-config" host-id]
           :metric latency})
  (>!! ch {:service "zookeeper.write-messaging.bytes"
           :state "ok"
           :tags ["monitoring-config" host-id]
           :metric bytes}))

(defn zookeeper-force-write-chunk [host-id ch config {:keys [latency bytes]}]
  (>!! ch {:service "zookeeper.force-write-chunk.latency"
           :state "ok"
           :tags ["monitoring-config" host-id]
           :metric latency})
  (>!! ch {:service "zookeeper.force-write-chunk.bytes"
           :state "ok"
           :tags ["monitoring-config" host-id]
           :metric bytes}))

(defn zookeeper-write-origin [host-id ch config {:keys [latency bytes]}]
  (>!! ch {:service "zookeeper.write-origin.latency"
           :state "ok"
           :tags ["monitoring-config" host-id]
           :metric latency})
  (>!! ch {:service "zookeeper.write-origin.bytes"
           :state "ok"
           :tags ["monitoring-config" host-id]
           :metric bytes}))

(defn zookeeper-read-catalog [host-id ch config {:keys [latency]}]
  (>!! ch {:service "zookeeper.read-catalog.latency"
           :state "ok"
           :tags ["monitoring-config" host-id]
           :metric latency}))

(defn zookeeper-read-workflow [host-id ch config {:keys [latency]}]
  (>!! ch {:service "zookeeper.read-workflow.latency"
           :state "ok"
           :tags ["monitoring-config" host-id]
           :metric latency}))

(defn zookeeper-read-flow-conditions [host-id ch config {:keys [latency]}]
  (>!! ch {:service "zookeeper.read-flow-conditions.latency"
           :state "ok"
           :tags ["monitoring-config" host-id]
           :metric latency}))

(defn zookeeper-read-lifecycles [host-id ch config {:keys [latency]}]
  (>!! ch {:service "zookeeper.read-lifecycles.latency"
           :state "ok"
           :tags ["monitoring-config" host-id]
           :metric latency}))

(defn zookeeper-read-task [host-id ch config {:keys [latency]}]
  (>!! ch {:service "zookeeper.read-task.latency"
           :state "ok"
           :tags ["monitoring-config" host-id]
           :metric latency}))

(defn zookeeper-read-chunk [host-id ch config {:keys [latency]}]
  (>!! ch {:service "zookeeper.read-chunk.latency"
           :state "ok"
           :tags ["monitoring-config" host-id]
           :metric latency}))

(defn zookeeper-read-origin [host-id ch config {:keys [latency]}]
  (>!! ch {:service "zookeeper.read-origin.latency"
           :state "ok"
           :tags ["monitoring-config" host-id]
           :metric latency}))

(defn zookeeper-read-job-scheduler [host-id ch config {:keys [latency]}]
  (>!! ch {:service "zookeeper.read-job-scheduler.latency"
           :state "ok"
           :tags ["monitoring-config" host-id]
           :metric latency}))

(defn zookeeper-read-messaging [host-id ch config {:keys [latency]}]
  (>!! ch {:service "zookeeper.read-messaging.latency"
           :state "ok"
           :tags ["monitoring-config" host-id]
           :metric latency}))

(defn zookeeper-gc-log-entry [host-id ch config {:keys [latency position]}]
  (>!! ch {:service "zookeeper.gc-log-entry.latency"
           :state "ok"
           :tags ["monitoring-config" host-id]
           :metric latency})
  (>!! ch {:service "zookeeper.gc-log-entry.position"
           :state "ok"
           :tags ["monitoring-config" host-id]
           :metric position}))

(defn peer-gc-peer-link [host-id ch config event]
  (>!! ch {:service "peer.gc-peer-link.event" 
           :tags ["monitoring-config" host-id]
           :state "ok"}))

(defn peer-backpressure-on [host-id ch config {:keys [id]}]
  (>!! ch {:service "peer.backpressure-on.event" 
           :tags ["monitoring-config" host-id]
           :state "ok"}))

(defn peer-backpressure-off [host-id ch config {:keys [id]}]
  (>!! ch {:service "peer.backpressure-off.event" 
           :tags ["monitoring-config" host-id]
           :state "ok"}))

(defn peer-prepare-join [host-id ch config {:keys [id]}]
  (>!! ch {:service "peer.prepare-join.event" 
           :tags ["monitoring-config" host-id]
           :state "ok"}))

(defn peer-accept-join [host-id ch config {:keys [id]}]
  (>!! ch {:service "peer.accept-join.event" 
           :tags ["monitoring-config" host-id]
           :state "ok"}))

(defn peer-notify-join [host-id ch config {:keys [id]}]
  (>!! ch {:service "peer.notify-join.event" 
           :tags ["monitoring-config" host-id]
           :state "ok"}))

(defn peer-try-complete-job [host-id ch config event]
  (>!! ch {:service "peer.try-complete-job.event" 
           :tags ["monitoring-config" host-id]
           :state "ok"}))

;; Frequent, perf sensitive operations
(defn peer-ack-segments [rate+latency config {:keys [latency]}]
  (im/update! rate+latency latency))

(defn peer-retry-segment [rate+latency config {:keys [latency]}]
  (im/update! rate+latency latency))

(defn peer-complete-message [rate+latency config {:keys [latency]}]
  (im/update! rate+latency latency))

(defn window-log-compaction [rate+latency config {:keys [latency]}]
  (im/update! rate+latency latency))

(defn window-log-playback [rate+latency config {:keys [latency]}]
  (im/update! rate+latency latency))

(defn window-log-write-entry [rate+latency config {:keys [latency]}]
  (im/update! rate+latency latency))

(defn snapshot-latencies+event [host-id service rate+latency]
  (let [latency-snapshot (im/snapshot! rate+latency)
        latencies (:latencies latency-snapshot)]
    (if-let [value (get latencies 1.0)]
      {:service service
       :window "1s"
       :quantile 1.0
       :value value
       :tags ["onyx" service host-id]})))

(defn messenger-queue-count [monitoring-config queue-count event]
  (let [{:keys [id task]} (:task-information monitoring-config)] 
    (swap! queue-count (fn [m] (assoc m (list id (:name task)) (:count event))))))

(defn messenger-queue-count-unregister [monitoring-config queue-count event]
  (let [{:keys [id task]} (:task-information monitoring-config)] 
    (swap! queue-count dissoc (list id (:name task)))))

(defn monitoring-config [host-id buf-capacity]
  (let [ch (chan (sliding-buffer buf-capacity))
        host-id (str host-id)
        complete-message-latency (im/rate+latency {:rate-unit :milliseconds
                                                   :latencies :milliseconds
                                                   :quantiles [0.5 0.9 0.95 0.99 0.999 1.0]})
        retry-message-latency (im/rate+latency {:rate-unit :milliseconds
                                                :latencies :milliseconds
                                                :quantiles [0.5 0.9 0.95 0.99 0.999 1.0]})

        ack-segments-latency (im/rate+latency {:rate-unit :milliseconds
                                               :latencies :milliseconds
                                               :quantiles [0.5 0.9 0.95 0.99 0.999 1.0]})

        window-log-playback-latency (im/rate+latency {:rate-unit :milliseconds
                                                      :latencies :milliseconds
                                                      :quantiles [0.5 0.9 0.95 0.99 0.999 1.0]})

        window-log-write-latency (im/rate+latency {:rate-unit :milliseconds
                                                   :latencies :milliseconds
                                                   :quantiles [0.5 0.9 0.95 0.99 0.999 1.0]})

        window-log-compaction-latency (im/rate+latency {:rate-unit :milliseconds
                                                        :latencies :milliseconds
                                                        :quantiles [0.5 0.9 0.95 0.99 0.999 1.0]})
        messenger-queue-counts (atom {})
        periodic-ch (go-loop []
                             (let [timeout-ch (timeout 1000)
                                   v (<!! timeout-ch)]
                               (run! (fn [[[peer-id task-name] cnt]]
                                       (>!! ch {:service "task.messenger-buffer count" 
                                                :value cnt 
                                                :tags ["monitoring-config" (str task-name) (str peer-id)]
                                                :state "ok"}))
                                     @messenger-queue-counts)
                               (some->> ack-segments-latency
                                        (snapshot-latencies+event host-id "peer.ack-segments.latency max")
                                        (>!! ch))
                               (some->> complete-message-latency
                                        (snapshot-latencies+event host-id "peer.complete-message.latency max")
                                        (>!! ch))
                               (some->> retry-message-latency
                                        (snapshot-latencies+event host-id "peer.retry-segment.latency max")
                                        (>!! ch))
                               (some->> window-log-playback-latency 
                                        (snapshot-latencies+event host-id "window.log-playback.latency max")
                                        (>!! ch))
                               (some->> window-log-write-latency 
                                        (snapshot-latencies+event host-id "window.write-log-entry.latency max")
                                        (>!! ch))
                               (some->> window-log-compaction-latency 
                                        (snapshot-latencies+event host-id "window.log-compaction.latency max")
                                        (>!! ch)))
                             (recur))]
    {:monitoring :custom
     :host-id host-id
     :monitoring/ch ch
     :zookeeper-write-log-entry (partial zookeeper-write-log-entry host-id ch)
     :zookeeper-read-log-entry (partial zookeeper-read-log-entry host-id ch)
     :zookeeper-write-workflow (partial zookeeper-write-workflow host-id ch)
     :zookeeper-write-catalog (partial zookeeper-write-catalog host-id ch)
     :zookeeper-write-flow-conditions (partial zookeeper-write-flow-conditions host-id ch)
     :zookeeper-write-lifecycles (partial zookeeper-write-lifecycles host-id ch)
     :zookeeper-write-task (partial zookeeper-write-task host-id ch)
     :zookeeper-write-chunk (partial zookeeper-write-chunk host-id ch)
     :zookeeper-write-job-scheduler (partial zookeeper-write-job-scheduler host-id ch)
     :zookeeper-write-messaging (partial zookeeper-write-messaging host-id ch)
     :zookeeper-force-write-chunk (partial zookeeper-force-write-chunk host-id ch)
     :zookeeper-write-origin (partial zookeeper-write-origin host-id ch)
     :zookeeper-read-catalog (partial zookeeper-read-catalog host-id ch)
     :zookeeper-read-workflow (partial zookeeper-read-workflow host-id ch)
     :zookeeper-read-flow-conditions (partial zookeeper-read-flow-conditions host-id ch)
     :zookeeper-read-lifecycles (partial zookeeper-read-lifecycles host-id ch)
     :zookeeper-read-task (partial zookeeper-read-task host-id ch)
     :zookeeper-read-chunk (partial zookeeper-read-chunk host-id ch)
     :zookeeper-read-origin (partial zookeeper-read-origin host-id ch)
     :zookeeper-read-job-scheduler (partial zookeeper-read-job-scheduler host-id ch)
     :zookeeper-read-messaging (partial zookeeper-read-messaging host-id ch)
     :zookeeper-gc-log-entry (partial zookeeper-gc-log-entry host-id ch)
     :peer-gc-peer-link (partial peer-gc-peer-link host-id ch)
     :peer-backpressure-on (partial peer-backpressure-on host-id ch)
     :peer-backpressure-off (partial peer-backpressure-off host-id ch)
     :peer-prepare-join (partial peer-prepare-join host-id ch)
     :peer-notify-join (partial peer-notify-join host-id ch)
     :peer-accept-join (partial peer-accept-join host-id ch)
     ;; Perf sensitive operations
     :messenger-queue-count (fn [config op] 
                              (messenger-queue-count config messenger-queue-counts op))
     :messenger-queue-count-unregister (fn [config op] 
                                         (messenger-queue-count-unregister config messenger-queue-counts op))
     :peer-ack-segments (fn [config op] (peer-ack-segments ack-segments-latency config op))
     :peer-retry-segment (fn [config op] (peer-retry-segment retry-message-latency config op))
     :peer-complete-segment (fn [config op] (peer-complete-message complete-message-latency config op))
     :window-log-compaction (fn [config op] (window-log-compaction window-log-compaction-latency  config op))
     :window-log-playback (fn [config op] (window-log-playback window-log-playback-latency config op))
     :window-log-write-entry (fn [config op] (window-log-write-entry window-log-write-latency config op))}))
