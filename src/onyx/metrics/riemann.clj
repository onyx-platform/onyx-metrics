(ns onyx.metrics.riemann
  (:require [clojure.core.async :refer [chan >!! <!! alts!! timeout]]
            [riemann.client :as r]
            [taoensso.timbre :refer [info warn fatal]]
            [clojure.set :refer [rename-keys]]
            [interval-metrics.core :as im]))

(defn metric->riemann-event [metric]
  (-> metric 
      (assoc :state "ok")
      (rename-keys {:value :metric})
      (select-keys [:metric :state :service :tags]))) 

(def max-backoff-time 2000)

(defn next-sleep-time [current]
  (if (zero? current)
    10
    (min max-backoff-time (* 2 current))))

(defn read-batch [ch batch-size batch-timeout]
  (let [timeout-ch (timeout batch-timeout)] 
    (loop [events [] i 0]
      (if (< i batch-size)
        (if-let [v (first (alts!! [ch timeout-ch]))]
          (recur (conj events v) (inc i))
          events)
        events))))

(defn riemann-sender [{:keys [riemann/address riemann/port riemann/batch-size riemann/batch-timeout riemann/send-timeout] :as riemann-config} ch]
  (when (nil? address)
    (throw (ex-info "Invalid Riemann metrics configuration." riemann-config)))

  (future
    (let [batch-size (or batch-size 10)
          batch-timeout (or batch-timeout 50)
          defaulted-timeout (or send-timeout 4000)
          defaulted-port (if port
                           (Integer/parseInt (str port))
                           5555)
          client (r/tcp-client {:host address :port defaulted-port})
          timeout-count (atom 0)]
        
      (while (not (Thread/interrupted)) 
        (let [events (map metric->riemann-event (read-batch ch batch-size batch-timeout))]
          (when-not (empty? events) 
            (loop [sleep 0]
              ;; Exponential backoff to rate limit errors
              (when-not (zero? sleep) 
                (info (format "Message send timeout count %s. Backing off %s." @timeout-count sleep))
                (Thread/sleep sleep))

              (let [result (try
                             (-> client 
                                 (r/send-events events)
                                 (deref defaulted-timeout ::timeout))
                             (catch InterruptedException e
                               ;; Intentionally pass.
                               )
                             (catch Throwable e
                               (warn e "Lost riemann connection" address port)
                               ::exception))]
                (when (#{::exception ::timeout} result)
                  (swap! timeout-count inc)
                  (recur (next-sleep-time sleep)))))))))))
