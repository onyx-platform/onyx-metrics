# onyx-metrics

Onyx Lifecycle plugin for instrumenting workflows. Track throughput and metrics of your tasks and log the output to Timbre and/or dashboard via websockets.

#### Installation

In your project file:

```clojure
[org.onyxplatform/onyx-metrics "0.10.0.0-technical-preview-4"]
```

#### JMX Metrics

Computes numerous metrics to JMX. You can inspect these with any tools that can
access JMX. We personally recommend attaching to the process with java mission
control (run jmc, assuming you have the Oracle Java SDK), or try using JMX exporters for your metrics systems.

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
