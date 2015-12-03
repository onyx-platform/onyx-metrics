(ns onyx.lifecycle.metrics.websocket
  (:require [taoensso.timbre :refer [warn] :as timbre]
            [clojure.core.async :refer [chan >!! <!! close! sliding-buffer]]
            [gniazdo.core :as ws]))

(defn websocket-sender [lifecycle ch]
  (future
    (let [conn (ws/connect (:websocket/address lifecycle))] 
      (loop []
        (when-let [metric-msg (<!! ch)]
          (try
            (ws/send-msg conn (pr-str metric-msg))
            (catch InterruptedException e
              ;; Intentionally pass.
              )))
        (recur)))))
