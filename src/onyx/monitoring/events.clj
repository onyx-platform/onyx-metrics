(ns onyx.monitoring.events
  (:require [clojure.core.async :refer [chan >!! <!! sliding-buffer alts!! timeout go-loop thread]]
            [taoensso.timbre :refer [warn info]]
            [interval-metrics.core :as im]
            [riemann.client :as r]))

(defn zookeeper-write-log-entry [ch config {:keys [latency bytes]}]
  (>!! ch {:service "zookeeper.write-log-entry.latency"
           :state "ok"
           :tags ["monitoring-config"]
           :metric latency})
  (>!! ch {:service "zookeeper.write-log-entry.bytes"
           :state "ok"
           :tags ["monitoring-config"]
           :metric bytes}))

(defn zookeeper-read-log-entry [ch config {:keys [latency bytes]}]
  (>!! ch {:service "zookeeper.read-log-entry.latency"
           :state "ok"
           :tags ["monitoring-config"]
           :metric latency}))

(defn zookeeper-write-catalog [ch config {:keys [latency bytes]}]
  (>!! ch {:service "zookeeper.write-catalog.latency"
           :state "ok"
           :tags ["monitoring-config"]
           :metric latency})
  (>!! ch {:service "zookeeper.write-catalog.bytes"
           :state "ok"
           :tags ["monitoring-config"]
           :metric bytes}))

(defn zookeeper-write-workflow [ch config {:keys [latency bytes]}]
  (>!! ch {:service "zookeeper.write-workflow.latency"
           :state "ok"
           :tags ["monitoring-config"]
           :metric latency})
  (>!! ch {:service "zookeeper.write-workflow.bytes"
           :state "ok"
           :tags ["monitoring-config"]
           :metric bytes}))

(defn zookeeper-write-flow-conditions [ch config {:keys [latency bytes]}]
  (>!! ch {:service "zookeeper.write-flow-conditions.latency"
           :state "ok"
           :tags ["monitoring-config"]
           :metric latency})
  (>!! ch {:service "zookeeper.write-flow-conditions.bytes"
           :state "ok"
           :tags ["monitoring-config"]
           :metric bytes}))

(defn zookeeper-write-lifecycles [ch config {:keys [latency bytes]}]
  (>!! ch {:service "zookeeper.write-lifecycles.latency"
           :state "ok"
           :tags ["monitoring-config"]
           :metric latency})
  (>!! ch {:service "zookeeper.write-lifecycles.bytes"
           :state "ok"
           :tags ["monitoring-config"]
           :metric bytes}))

(defn zookeeper-write-task [ch config {:keys [latency bytes]}]
  (>!! ch {:service "zookeeper.write-task.latency"
           :state "ok"
           :tags ["monitoring-config"]
           :metric latency})
  (>!! ch {:service "zookeeper.write-task.bytes"
           :state "ok"
           :tags ["monitoring-config"]
           :metric bytes}))

(defn zookeeper-write-chunk [ch config {:keys [latency bytes]}]
  (>!! ch {:service "zookeeper.write-chunk.latency"
           :state "ok"
           :tags ["monitoring-config"]
           :metric latency})
  (>!! ch {:service "zookeeper.write-chunk.bytes"
           :state "ok"
           :tags ["monitoring-config"]
           :metric bytes}))

(defn zookeeper-write-log-parameters [ch config {:keys [latency bytes]}]
  (>!! ch {:service "zookeeper.write-log-parameters.latency"
           :state "ok"
           :tags ["monitoring-config"]
           :metric latency})
  (>!! ch {:service "zookeeper.write-log-parameters.bytes"
           :state "ok"
           :tags ["monitoring-config"]
           :metric bytes}))

(defn zookeeper-force-write-chunk [ch config {:keys [latency bytes]}]
  (>!! ch {:service "zookeeper.force-write-chunk.latency"
           :state "ok"
           :tags ["monitoring-config"]
           :metric latency})
  (>!! ch {:service "zookeeper.force-write-chunk.bytes"
           :state "ok"
           :tags ["monitoring-config"]
           :metric bytes}))

(defn zookeeper-write-origin [ch config {:keys [latency bytes]}]
  (>!! ch {:service "zookeeper.write-origin.latency"
           :state "ok"
           :tags ["monitoring-config"]
           :metric latency})
  (>!! ch {:service "zookeeper.write-origin.bytes"
           :state "ok"
           :tags ["monitoring-config"]
           :metric bytes}))

(defn zookeeper-read-catalog [ch config {:keys [latency]}]
  (>!! ch {:service "zookeeper.read-catalog.latency"
           :state "ok"
           :tags ["monitoring-config"]
           :metric latency}))

(defn zookeeper-read-workflow [ch config {:keys [latency]}]
  (>!! ch {:service "zookeeper.read-workflow.latency"
           :state "ok"
           :tags ["monitoring-config"]
           :metric latency}))

(defn zookeeper-read-flow-conditions [ch config {:keys [latency]}]
  (>!! ch {:service "zookeeper.read-flow-conditions.latency"
           :state "ok"
           :tags ["monitoring-config"]
           :metric latency}))

(defn zookeeper-read-lifecycles [ch config {:keys [latency]}]
  (>!! ch {:service "zookeeper.read-lifecycles.latency"
           :state "ok"
           :tags ["monitoring-config"]
           :metric latency}))

(defn zookeeper-read-task [ch config {:keys [latency]}]
  (>!! ch {:service "zookeeper.read-task.latency"
           :state "ok"
           :tags ["monitoring-config"]
           :metric latency}))

(defn zookeeper-read-chunk [ch config {:keys [latency]}]
  (>!! ch {:service "zookeeper.read-chunk.latency"
           :state "ok"
           :tags ["monitoring-config"]
           :metric latency}))

(defn zookeeper-read-origin [ch config {:keys [latency]}]
  (>!! ch {:service "zookeeper.read-origin.latency"
           :state "ok"
           :tags ["monitoring-config"]
           :metric latency}))

(defn zookeeper-read-log-parameters [ch config {:keys [latency]}]
  (>!! ch {:service "zookeeper.read-log-parameters.latency"
           :state "ok"
           :tags ["monitoring-config"]
           :metric latency}))

(defn zookeeper-gc-log-entry [ch config {:keys [latency position]}]
  (>!! ch {:service "zookeeper.gc-log-entry.latency"
           :state "ok"
           :tags ["monitoring-config"]
           :metric latency})
  (>!! ch {:service "zookeeper.gc-log-entry.position"
           :state "ok"
           :tags ["monitoring-config"]
           :metric position}))

(defn peer-gc-peer-link [ch config event]
  (>!! ch {:service "peer.gc-peer-link.event" 
           :tags ["monitoring-config"]
           :state "ok"}))

(defn peer-backpressure-on [ch config {:keys [id]}]
  (>!! ch {:service "peer.backpressure-on.event" 
           :tags ["monitoring-config"]
           :state "ok"}))

(defn peer-backpressure-off [ch config {:keys [id]}]
  (>!! ch {:service "peer.backpressure-off.event" 
           :tags ["monitoring-config"]
           :state "ok"}))

(defn peer-prepare-join [ch config {:keys [id]}]
  (>!! ch {:service "peer.prepare-join.event" 
           :tags ["monitoring-config"]
           :state "ok"}))

(defn peer-accept-join [ch config {:keys [id]}]
  (>!! ch {:service "peer.accept-join.event" 
           :tags ["monitoring-config"]
           :state "ok"}))

(defn peer-notify-join [ch config {:keys [id]}]
  (>!! ch {:service "peer.notify-join.event" 
           :tags ["monitoring-config"]
           :state "ok"}))

(defn peer-try-complete-job [ch config event]
  (>!! ch {:service "peer.try-complete-job.event" 
           :tags ["monitoring-config"]
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

(defn snapshot-latencies+event [service rate+latency]
  (let [latency-snapshot (im/snapshot! rate+latency)
        latencies (:latencies latency-snapshot)]
    (if-let [value (get latencies 1.0)]
      {:service service
       :window "1s"
       :quantile 1.0
       :value value
       :tags ["onyx" service "latency"]})))

(defn messenger-queue-count [monitoring-config queue-count event]
  (let [{:keys [id task]} (:task-information monitoring-config)] 
    (swap! queue-count (fn [m] (assoc m (list id (:name task)) (:count event))))))

(defn messenger-queue-count-unregister [monitoring-config queue-count event]
  (let [{:keys [id task]} (:task-information monitoring-config)] 
    (swap! queue-count dissoc (list id (:name task)))))

(defn monitoring-config [buf-capacity]
  (let [ch (chan (sliding-buffer buf-capacity))
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
                                       (>!! ch {:service (str (format "[%s] task-messenger-buffer-count" task-name)) 
                                                :value cnt 
                                                :tags ["monitoring-config" (str task-name) (str peer-id)]
                                                :state "ok"}))
                                     @messenger-queue-counts)
                               ;; TODO, further segment peer-ack-segments, peer-retry-segments, peer-complete-segment by task-id
                               (some->> ack-segments-latency
                                        (snapshot-latencies+event "peer.ack-segments.latency max")
                                        (>!! ch))
                               (some->> complete-message-latency
                                        (snapshot-latencies+event "peer.complete-message.latency max")
                                        (>!! ch))
                               (some->> retry-message-latency
                                        (snapshot-latencies+event "peer.retry-segment.latency max")
                                        (>!! ch))
                               (some->> window-log-playback-latency 
                                        (snapshot-latencies+event "window.log-playback.latency max")
                                        (>!! ch))
                               (some->> window-log-write-latency 
                                        (snapshot-latencies+event "window.write-log-entry.latency max")
                                        (>!! ch))
                               (some->> window-log-compaction-latency 
                                        (snapshot-latencies+event "window.log-compaction.latency max")
                                        (>!! ch)))
                             (recur))]
    {:monitoring :custom
     :monitoring/ch ch
     :zookeeper-write-log-entry (partial zookeeper-write-log-entry ch)
     :zookeeper-read-log-entry (partial zookeeper-read-log-entry ch)
     :zookeeper-write-workflow (partial zookeeper-write-workflow ch)
     :zookeeper-write-catalog (partial zookeeper-write-catalog ch)
     :zookeeper-write-flow-conditions (partial zookeeper-write-flow-conditions ch)
     :zookeeper-write-lifecycles (partial zookeeper-write-lifecycles ch)
     :zookeeper-write-task (partial zookeeper-write-task ch)
     :zookeeper-write-chunk (partial zookeeper-write-chunk ch)
     :zookeeper-write-log-parameters (partial zookeeper-write-log-parameters ch)
     :zookeeper-force-write-chunk (partial zookeeper-force-write-chunk ch)
     :zookeeper-write-origin (partial zookeeper-write-origin ch)
     :zookeeper-read-catalog (partial zookeeper-read-catalog ch)
     :zookeeper-read-workflow (partial zookeeper-read-workflow ch)
     :zookeeper-read-flow-conditions (partial zookeeper-read-flow-conditions ch)
     :zookeeper-read-lifecycles (partial zookeeper-read-lifecycles ch)
     :zookeeper-read-task (partial zookeeper-read-task ch)
     :zookeeper-read-chunk (partial zookeeper-read-chunk ch)
     :zookeeper-read-origin (partial zookeeper-read-origin ch)
     :zookeeper-read-log-parameters (partial zookeeper-read-log-parameters ch)
     :zookeeper-gc-log-entry (partial zookeeper-gc-log-entry ch)
     :peer-gc-peer-link (partial peer-gc-peer-link ch)
     :peer-backpressure-on (partial peer-backpressure-on ch)
     :peer-backpressure-off (partial peer-backpressure-off ch)
     :peer-prepare-join (partial peer-prepare-join ch)
     :peer-notify-join (partial peer-notify-join ch)
     :peer-accept-join (partial peer-accept-join ch)
     ;; Perf sensitive operations
     :messenger-queue-count (fn [config op] 
                              (messenger-queue-count config messenger-queue-counts op))
     :messenger-queue-count-unregister (fn [config op] 
                                         (messenger-queue-count-unregister config messenger-queue-counts op))
     ;; TODO, further segment peer-ack-segments, peer-retry-segments, peer-complete-segment by task-id
     :peer-ack-segments (fn [config op] (peer-ack-segments ack-segments-latency config op))
     :peer-retry-segment (fn [config op] (peer-retry-segment retry-message-latency config op))
     :peer-complete-segment (fn [config op] (peer-complete-message complete-message-latency config op))
     :window-log-compaction (fn [config op] (window-log-compaction window-log-compaction-latency  config op))
     :window-log-playback (fn [config op] (window-log-playback window-log-playback-latency config op))
     :window-log-write-entry (fn [config op] (window-log-write-entry window-log-write-latency config op))}))
