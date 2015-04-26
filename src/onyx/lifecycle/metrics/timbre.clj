(ns onyx.lifecycle.metrics.timbre
  (:require [taoensso.timbre :as timbre]))

(defn quantile
  ([p vs]
     (let [svs (sort vs)]
       (quantile p (count vs) svs (first svs) (last svs))))
  ([p c svs mn mx]
     (let [pic (* p (inc c))
           k (int pic)
           d (- pic k)
           ndk (if (zero? k) mn (nth svs (dec k)))]
       (cond
        (zero? k) mn
        (= c (dec k)) mx
        (= c k) mx
        :else (+ ndk (* d (- (nth svs k) ndk)))))))

(defn before-task [event lifecycle]
  {:onyx.metrics/timbre-fut
   (future
     (try
       (loop []
         (Thread/sleep (:timbre/interval-ms lifecycle))
         (let [state @(:onyx.metrics/state event)]
           (when-let [throughput (:throughput state)]
             (taoensso.timbre/info (format "[%s] Task [%s] :: Throughput 10s :: %s segments"
                                           (:onyx.core/id event) (:onyx.core/task-id event)
                                           (apply + (map #(apply + %) (take 10 throughput)))))
             (taoensso.timbre/info (format "[%s] Task [%s] :: Throughput 30s :: %s segments"
                                           (:onyx.core/id event) (:onyx.core/task-id event)
                                           (apply + (map #(apply + %) (take 30 throughput)))))
             (taoensso.timbre/info (format "[%s] Task [%s] :: Throughput 60s :: %s segments"
                                           (:onyx.core/id event) (:onyx.core/task-id event)
                                           (apply + (map #(apply + %) (take 60 throughput))))))
           (when-let [latency (:latency state)]
             (taoensso.timbre/info (format "[%s] Task [%s] :: Batch Latency 10s 50th Percentile :: %s ms"
                                           (:onyx.core/id event) (:onyx.core/task-id event)
                                           (quantile 0.50 (apply concat (take 10 latency)))))
             (taoensso.timbre/info (format "[%s] Task [%s] :: Batch Latency 10s 90th Percentile :: %s ms"
                                           (:onyx.core/id event) (:onyx.core/task-id event)
                                           (quantile 0.90 (apply concat (take 10 latency)))))
             (taoensso.timbre/info (format "[%s] Task [%s] :: Batch Latency 10s 99th Percentile :: %s ms"
                                           (:onyx.core/id event) (:onyx.core/task-id event)
                                           (quantile 0.99 (apply concat (take 10 latency)))))

             (taoensso.timbre/info (format "[%s] Task [%s] :: Batch Latency 30s 50th Percentile :: %s ms"
                                           (:onyx.core/id event) (:onyx.core/task-id event)
                                           (quantile 0.50 (apply concat (take 30 latency)))))
             (taoensso.timbre/info (format "[%s] Task [%s] :: Batch Latency 30s 90th Percentile :: %s ms"
                                           (:onyx.core/id event) (:onyx.core/task-id event)
                                           (quantile 0.90 (apply concat (take 30 latency)))))
             (taoensso.timbre/info (format "[%s] Task [%s] :: Batch Latency 30s 99th Percentile :: %s ms"
                                           (:onyx.core/id event) (:onyx.core/task-id event)
                                           (quantile 0.99 (apply concat (take 30 latency)))))

             (taoensso.timbre/info (format "[%s] Task [%s] :: Batch Latency 60s 50th Percentile :: %s ms"
                                           (:onyx.core/id event) (:onyx.core/task-id event)
                                           (quantile 0.50 (apply concat (take 60 latency)))))
             (taoensso.timbre/info (format "[%s] Task [%s] :: Batch Latency 60s 90th Percentile :: %s ms"
                                           (:onyx.core/id event) (:onyx.core/task-id event)
                                           (quantile 0.90 (apply concat (take 60 latency)))))
             (taoensso.timbre/info (format "[%s] Task [%s] :: Batch Latency 60s 99th Percentile :: %s ms"
                                           (:onyx.core/id event) (:onyx.core/task-id event)
                                           (quantile 0.99 (apply concat (take 60 latency))))))
           (recur)))
       (catch InterruptedException e)
       (catch Throwable e
         (timbre/fatal e))))})

(defn after-task [event lifecycle]
  (future-cancel (:onyx.metrics/timbre-fut event))
  {})

(def calls
  {:lifecycle/before-task :onyx.lifecycle.metrics.timbre/before-task
   :lifecycle/after-task :onyx.lifecycle.metrics.timbre/after-task})
