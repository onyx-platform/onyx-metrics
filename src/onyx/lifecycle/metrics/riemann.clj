(ns onyx.lifecycle.metrics.riemann
  (:require [clojure.core.async :refer [chan >!! <!! dropping-buffer]]
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

(defn riemann-sender [{:keys [riemann/address riemann/port riemann/send-timeout] :as riemann-config} ch]
  (when (nil? address)
    (throw (ex-info "Invalid Riemann metrics configuration." riemann-config)))

  (future
    (let [defaulted-timeout (or send-timeout 4000)
          defaulted-port (or port 5555)
          _ (info "Connecting to riemann server @" address port)
          client (r/tcp-client {:host address :port defaulted-port})
          timeout-count (atom 0)]
      (loop [sleep 0]
        ;; Exponential backoff to rate limit errors
        (when-not (zero? sleep) 
          (info (format "Message send timeout count %s. Backing off %s.") @timeout-count sleep)
          (Thread/sleep sleep))

        ;; TODO, batch sends
        (when-let [metric-msg (<!! ch)]
          (recur 
            (let [riemann-event (metric->riemann-event metric-msg)]
              (try
                (when (= ::timeout 
                         (-> client 
                             (r/send-event riemann-event)
                             (deref defaulted-timeout ::timeout)))
                  ;; Retry message
                  ;; Replace with core.async offer when it is part of core.async
                  ;; If the metrics buffer is already full then there's no point adding to the problem
                  (>!! ch metric-msg)
                  (swap! timeout-count inc))
                0
                (catch InterruptedException e
                  ;; Intentionally pass.
                  )
                (catch Throwable e
                  ;; Don't retry metrics on throw, otherwise we fill up the logs very quickly
                  (>!! ch metric-msg)
                  (warn e "Lost riemann connection" address port)
                  (next-sleep-time sleep))))))))))
