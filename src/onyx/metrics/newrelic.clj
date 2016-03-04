(ns onyx.metrics.newrelic
  (:require [clojure.core.async :refer [chan >!! <!! alts!! timeout]]
            [taoensso.timbre :refer [info warn fatal]]
            [clojure.set :refer [rename-keys]]
            [clj-http.client :as client])
  (:import [java.net InetAddress]))

(def metric->units 
  {:throughput "segments"
   :retry-rate "retries"
   :complete-latency "ms"
   :batch-latency "ms"
   :pending-messages-count "count"})

(defn metrics->components [job-name period metrics]
  {"name" (str job-name)
   "guid" "org.onyxplatform"
   "duration" (or period 0)
   "metrics" (into {} 
                   (map (fn [metric]
                          [(format "Component/%s/%s[%s]" 
                                   (name (:task-name metric)) 
                                   (:label metric) 
                                   (metric->units (:metric metric) "unknown")) 
                           (:value metric)])
                        metrics))}) 

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

(defn warn-failure [e events]
  (warn e "Failed to send events to newrelic" events))

(defn newrelic-sender [{:keys [newrelic/batch-size newrelic/batch-timeout newrelic/license-key]} ch]
  (let [batch-size (or batch-size 50)
        batch-timeout (or batch-timeout 500)
        timeout-count (atom 0)
        license-key (or (System/getenv "NEW_RELIC_LICENSE_KEY") 
                        license-key
                        (throw (Exception. "NewRelic license key must be supplied via the NEW_RELIC_LICENSE_KEY environment variable or :newrelic/license-key")))] 
    (future
      (while (not (Thread/interrupted)) 
        (let [events (read-batch ch batch-size batch-timeout)
              events-by-period (group-by (juxt :job-name :period) events)]
          (when-not (empty? events) 
            (loop [sleep 0]
              ;; Exponential backoff to rate limit errors
              (when-not (zero? sleep) 
                (info (format "Message send timeout count %s. Backing off %s." @timeout-count sleep))
                (Thread/sleep sleep))

              (let [result (try
                             (doall 
                               (pmap (fn [[[job-name period] evs]]
                                       (client/post "https://platform-api.newrelic.com/platform/v1/metrics" 
                                                    {:headers {"X-License-Key" license-key}
                                                     :form-params {"agent" {"host" (str (.getHostName (InetAddress/getLocalHost))) 
                                                                            "version" "1.0.0"}
                                                                   "components" [(metrics->components job-name period evs)]}
                                                     :content-type :json}))
                                     events-by-period))
                             (catch InterruptedException e
                               ;; Intentionally pass.
                               )
                             (catch Throwable e
                               (run! #(>!! ch %) events)
                               (warn-failure e events)
                               ::exception))]
                (when (#{::exception} result)
                  (swap! timeout-count inc)
                  (recur (next-sleep-time sleep)))))))))))
