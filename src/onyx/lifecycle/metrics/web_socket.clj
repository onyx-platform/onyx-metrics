(ns onyx.lifecycle.metrics.web-socket
  (:require [taoensso.timbre :refer [warn] :as timbre]
            [onyx.lifecycle.metrics.common :refer [quantile]]
            [gniazdo.core :as ws]))

(defn before-task [event lifecycle]
  (let [conn (ws/connect (:web-socket/address lifecycle))]
    {:onyx.metrics/web-socket-conn conn
     :onyx.metrics/web-socket-fut
     (future
       (try
         (loop []
           (Thread/sleep (:web-socket/interval-ms lifecycle))
           (let [state @(:onyx.metrics/state event)
                 core {:job-id (:onyx.core/job-id event)
                       :task-id (:onyx.core/task-id event)
                       :peer-id (:onyx.core/id event)}]
             (when-let [throughput (:throughput state)]
               (try
                 (ws/send-msg conn (pr-str (merge
                                            core
                                            {:metric :throughput
                                             :window "10s"
                                             :throughput (apply + (map #(apply + %) (take 10 throughput)))})))
                 (ws/send-msg conn (pr-str (merge
                                            core
                                            {:metric :throughput
                                             :window "30s"
                                             :throughput (apply + (map #(apply + %) (take 30 throughput)))})))
                 (ws/send-msg conn (pr-str (merge
                                            core
                                            {:metric :throughput
                                             :window "60s"
                                             :throughput (apply + (map #(apply + %) (take 60 throughput)))})))
                 (catch Throwable e
                   (warn e))))

             (when-let [latency (:latency state)]
               (try
                 (ws/send-msg conn (pr-str (merge
                                            core
                                            {:metric :latency
                                             :window "10s"
                                             :quantile 0.50
                                             :latency (quantile 0.50 (apply concat (take 10 latency)))})))
                 (ws/send-msg conn (pr-str (merge
                                            core
                                            {:metric :latency
                                             :window "10s"
                                             :quantile 0.90
                                             :latency (quantile 0.90 (apply concat (take 10 latency)))})))
                 (ws/send-msg conn (pr-str (merge
                                            core
                                            {:metric :latency
                                             :window "10s"
                                             :quantile 0.99
                                             :latency (quantile 0.99 (apply concat (take 10 latency)))})))

                 (ws/send-msg conn (pr-str (merge
                                            core
                                            {:metric :latency
                                             :window "30s"
                                             :quantile 0.50
                                             :latency (quantile 0.50 (apply concat (take 30 latency)))})))
                 (ws/send-msg conn (pr-str (merge
                                            core
                                            {:metric :latency
                                             :window "30s"
                                             :quantile 0.90
                                             :latency (quantile 0.90 (apply concat (take 30 latency)))})))
                 (ws/send-msg conn (pr-str (merge
                                            core
                                            {:metric :latency
                                             :window "30s"
                                             :quantile 0.99
                                             :latency (quantile 0.99 (apply concat (take 30 latency)))})))

                 (ws/send-msg conn (pr-str (merge
                                            core
                                            {:metric :latency
                                             :window "60s"
                                             :quantile 0.50
                                             :latency (quantile 0.50 (apply concat (take 60 latency)))})))
                 (ws/send-msg conn (pr-str (merge
                                            core
                                            {:metric :latency
                                             :window "60s"
                                             :quantile 0.90
                                             :latency (quantile 0.90 (apply concat (take 60 latency)))})))
                 (ws/send-msg conn (pr-str (merge
                                            core
                                            {:metric :latency
                                             :window "60s"
                                             :quantile 0.99
                                             :latency (quantile 0.99 (apply concat (take 60 latency)))})))
                 (catch Throwable e
                   (warn e))))
             (recur)))
         (catch InterruptedException e)
         (catch Throwable e
           (timbre/fatal e))))}))

(defn after-task [event lifecycle]
  (ws/close (:onyx.metrics/web-socket-conn event))
  (future-cancel (:onyx.metrics/web-socket-fut event))
  {})

(def calls
  {:lifecycle/before-task :onyx.lifecycle.metrics.web-socket/before-task
   :lifecycle/after-task :onyx.lifecycle.metrics.web-socket/after-task})
