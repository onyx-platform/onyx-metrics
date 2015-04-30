(ns onyx.lifecycle.metrics.common)

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
