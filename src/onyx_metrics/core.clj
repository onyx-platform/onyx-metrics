(ns onyx-metrics.core
  (:require [rotating-seq.core :as rsc]))

(def r (rsc/create-r-seq 60000 20000))

(rsc/expire-bucket (rsc/expire-bucket (rsc/expire-bucket (rsc/add-to-head (rsc/add-to-head r [1 2 3]) [4 5]))))

