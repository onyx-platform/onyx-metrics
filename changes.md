# 0.8.0.4
- *BREAKING CHANGE* Further segment metrics by peer-id, otherwise riemann and others will think measurements from different peers on the same task are just another reading


# 0.7.10
- *BREAKING CHANGE* There is no need for separate latency and throughput lifecycles as these are now part of :onyx.lifecycle.metrics.riemann/calls, and :onyx.lifecycle.metrics.timbre/calls. Please refer to the README for the new lifecycle calls and configuration. 
- New metrics "complete-segment-latency" and "retry-segment-rate"
