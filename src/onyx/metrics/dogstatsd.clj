(ns onyx.metrics.dogstatsd
  (:require [clojure.core.async :refer [<!!]]
            [clojure.string :as string]
            [cognician.dogstatsd :as dogstatsd]
            [taoensso.timbre :refer [warn error]]))

(def quantile->str-mapping
  {0.5   "50th"
   0.9   "90th"
   0.99  "99th"
   0.999 "99_9th"
   1.0   "max"})

(defn quantile->str [n]
  (assert (= (type n) java.lang.Double) "Quantile should be a double")
  (quantile->str-mapping n))

(defn str->dogstatsd-str
  "Converts a string to a dogstatsd suitable string."
  [s]
  (assert string? s)
  (-> s
      (string/replace #"-" "_")
      (string/replace #"/" "_")
      (string/replace #"[^a-zA-Z0-9_.]" "")))

(defn kw-or-str->dogstatsd-str
  [kw-or-s]
  (assert (or (keyword? kw-or-s) (string? kw-or-s)))
  (-> (if (string? kw-or-s)
        kw-or-s
        (str kw-or-s))
      str->dogstatsd-str))

(defn dogstatsd-metric-name
  [{:keys [task-name window quantile metric]}]
  (str (kw-or-str->dogstatsd-str task-name) "."
       (when window
         (str (kw-or-str->dogstatsd-str window) "_"))
       (when quantile
         (str (quantile->str quantile) "_"))
       (kw-or-str->dogstatsd-str metric)))

(defn metric->dogstatsd-metric
  [{:keys [value tags] :as metric}]
  {:service (dogstatsd-metric-name metric)
   :value   value
   :opts    {:tags tags}})

(defn dogstatsd-sender [{:keys [dogstatsd/url
                                dogstatsd/global-tags
                                dogstatsd/global-sample-rate]
                         :as   lifecycle}
                        ch
                        shutdown?]
  (when global-tags
    (assert (vector? global-tags)
            "Please use the vector format for tags [String ...]"))
  (when global-sample-rate
    (assert (= (type global-sample-rate) java.lang.Double)
            "Sample rate should be a double between 0 and 1"))
  (when (nil? url)
    (throw (ex-info
            "Invalid DogStatsD metrics config - missing URL" lifecycle)))
  (let [_ (dogstatsd/configure! url {:tags global-tags})]
    (future
      (loop []
        (when-not @shutdown?
          (when-let [metric-msg (<!! ch)]
            (try
             (let [{:keys [service value opts]} (metric->dogstatsd-metric metric-msg)]
               (when value
                 (dogstatsd/gauge! service
                                   value
                                   (cond-> opts
                                     global-sample-rate (assoc :sample-rate global-sample-rate)))))

             (catch InterruptedException e
               (throw e))

             (catch java.lang.AssertionError e
               (error e))

             (catch Throwable e
               (warn e "Lost dogstatsd connection: " url))))
          (recur))))))
