# onyx-metrics

Onyx Lifecycle plugin for instrumenting workflows. Track throughput and metrics of your tasks and log the output to Timbre and/or dashboard via websockets.

#### Installation

In your project file:

```clojure
[org.onyxplatform/onyx-metrics "0.9.6.1-SNAPSHOT"]
```

#### JMX Metrics

task_lifecycle_write_batch_Max{job=35d8d894-93df-458a-5ff5-e02375ce7d19, task=inc, peer-id=076a8cde-b9a1-7a34-b8c4-2ada16e4f25c} 2.389211
task_lifecycle_write_batch_Min{job=35d8d894-93df-458a-5ff5-e02375ce7d19, task=inc, peer-id=076a8cde-b9a1-7a34-b8c4-2ada16e4f25c} 3.06E-4
task_lifecycle_write_batch_75thPercentile{job=35d8d894-93df-458a-5ff5-e02375ce7d19, task=inc, peer-id=076a8cde-b9a1-7a34-b8c4-2ada16e4f25c} 5.89E-4
task_lifecycle_write_batch_95thPercentile{job=35d8d894-93df-458a-5ff5-e02375ce7d19, task=inc, peer-id=076a8cde-b9a1-7a34-b8c4-2ada16e4f25c} 0.001122
task_lifecycle_write_batch_98thPercentile{job=35d8d894-93df-458a-5ff5-e02375ce7d19, task=inc, peer-id=076a8cde-b9a1-7a34-b8c4-2ada16e4f25c} 0.0016619999999999998
task_lifecycle_write_batch_99thPercentile{job=35d8d894-93df-458a-5ff5-e02375ce7d19, task=inc, peer-id=076a8cde-b9a1-7a34-b8c4-2ada16e4f25c} 0.002263
task_lifecycle_write_batch_999thPercentile{job=35d8d894-93df-458a-5ff5-e02375ce7d19, task=inc, peer-id=076a8cde-b9a1-7a34-b8c4-2ada16e4f25c} 1.051202
task_lifecycle_write_batch_Mean{job=35d8d894-93df-458a-5ff5-e02375ce7d19, task=inc, peer-id=076a8cde-b9a1-7a34-b8c4-2ada16e4f25c} 0.003911516536964878
task_lifecycle_write_batch_StdDev{job=35d8d894-93df-458a-5ff5-e02375ce7d19, task=inc, peer-id=076a8cde-b9a1-7a34-b8c4-2ada16e4f25c} 0.0813197282724673
task_lifecycle_write_batch_50thPercentile{job=35d8d894-93df-458a-5ff5-e02375ce7d19, task=inc, peer-id=076a8cde-b9a1-7a34-b8c4-2ada16e4f25c} 4.48E-4
task_lifecycle_write_batch_Count{job=35d8d894-93df-458a-5ff5-e02375ce7d19, task=inc, peer-id=076a8cde-b9a1-7a34-b8c4-2ada16e4f25c} 5888
task_lifecycle_write_batch_FifteenMinuteRate{job=35d8d894-93df-458a-5ff5-e02375ce7d19, task=inc, peer-id=076a8cde-b9a1-7a34-b8c4-2ada16e4f25c} 0.0
task_lifecycle_write_batch_FiveMinuteRate{job=35d8d894-93df-458a-5ff5-e02375ce7d19, task=inc, peer-id=076a8cde-b9a1-7a34-b8c4-2ada16e4f25c} 0.0
task_lifecycle_write_batch_MeanRate{job=35d8d894-93df-458a-5ff5-e02375ce7d19, task=inc, peer-id=076a8cde-b9a1-7a34-b8c4-2ada16e4f25c} 4778.656641737592
task_lifecycle_write_batch_OneMinuteRate{job=35d8d894-93df-458a-5ff5-e02375ce7d19, task=inc, peer-id=076a8cde-b9a1-7a34-b8c4-2ada16e4f25c} 0.0

Computes the following metrics.
* Mean throughput, one minute rate, five minute rate, fifteen minute rate
* 50th, 90th, 99th, 99.9th, maxiumum percentile batch latency (time to process a batch, computed every 10s)

#### Lifecycle entries

Add these maps to your `:lifecycles` vector in the argument to `:onyx.api/submit-job`.

##### JMX metrics

Reports all metrics to JMX.

Use:

```clojure
(:require [onyx.lifecycle.metrics.metrics])
```

```clojure
{:lifecycle/task :all ; or :task-name for an individual task
 :lifecycle/calls :onyx.lifecycle.metrics.metrics/calls
 :lifecycle/doc "Instruments a task's metrics and sends to JMX"}
```

You may wish to name your job so that metrics are scoped to a job name, rather
than a job id, include the following in your job's metadata key:

```clojure
{:workflow ...
 :lifecycles ...
 :metadata {:name "YOURJOBNAME"}}
```

You can easily inspect the metrics via any supported JMX access method. One 

### Handy Tip

Sometimes, you may want a quick way to instrument all the tasks in a workflow.
This can be achieved by using :lifecycle/task :all for your given lifecycles.


## License

Copyright © 2015 Michael Drogalis

Distributed under the Eclipse Public License either version 1.0 or (at
your option) any later version.
