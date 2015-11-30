# 0.8.2.5
- Undo breaking change in 0.8.0.4. Include peer-id in tags, but not in service name.

# 0.8.2.4
- Fix test error causing non-release of 0.8.2.4

# 0.8.2.3
- Added measure for counting pending messages "pending_messages_count"
- *BREAKING CHANGE* percentile measurements with a period now use an underscore e.g. 99.9 -> 99_9. This is so they are not segmented in grafana and others.
- *BREAKING CHANGE* batch latency percentiles now include "th" in their name for consistency with the other services names.


# 0.8.0.4
- *BREAKING CHANGE* Further segment metrics by peer-id, otherwise riemann and others will think measurements from different peers on the same task are just another reading


# 0.7.10
- *BREAKING CHANGE* There is no need for separate latency and throughput lifecycles as these are now part of :onyx.lifecycle.metrics.riemann/calls, and :onyx.lifecycle.metrics.timbre/calls. Please refer to the README for the new lifecycle calls and configuration. 
- New metrics "complete-segment-latency" and "retry-segment-rate"
