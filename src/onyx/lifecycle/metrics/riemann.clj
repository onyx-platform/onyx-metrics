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

(defn riemann-sender [{:keys [riemann/address riemann/port riemann/send-timeout] :as lifecycle} ch timeout-count]
  (future
    (let [defaulted-timeout (or send-timeout 1000)
          client (r/tcp-client {:host address :port port})]
      (loop []
        (when-let [metric-msg (<!! ch)]
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
              (catch InterruptedException e
                ;; Intentionally pass.
                )
              (catch Throwable e
                ;; Don't retry metrics on throw, otherwise we fill up the logs very quickly
                (>!! ch metric-msg)
                (warn e)))))
        (recur)))))
