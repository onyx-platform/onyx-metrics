(ns onyx.lifecycle.metrics.timbre
  (:require [clojure.core.async :refer [chan >!! <!! dropping-buffer]]
            [taoensso.timbre :refer [info warn fatal]]
            [clojure.set :refer [rename-keys]]
            [interval-metrics.core :as im]))

(defn timbre-sender [lifecycle ch _]
  (future
    (loop []
      (when-let [metric-msg (<!! ch)]
        (try
          (info "Metrics:" metric-msg)
          (catch InterruptedException e
            ;; Intentionally pass.
            )))
      (recur))))
