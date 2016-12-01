# onyx-metrics

Onyx Lifecycle plugin for instrumenting workflows. Track throughput and metrics of your tasks and log the output to Timbre and/or a metrics server.

#### Installation

In your project file:

```clojure
[org.onyxplatform/onyx-metrics "0.9.15.1-SNAPSHOT"]
```

#### Guide to Types of Metrics & Diagnosing Issues

Firstly, please note that all metrics are reported per peer. Please be sure
to either visualize each measurement independently, or to aggregate/roll up reported metrics
correctly e.g sum throughputs, average/max/min complete latencies, etc.

##### Segment Completions

**Task types:** input

**Tags:**
* complete_latency_50th - complete latency (ms), 50th percentile, calculated over 10s period
* complete_latency_90th - complete latency (ms), 90th percentile, calculated over 10s period 
* complete_latency_99th - complete latency (ms), 99th percentile, calculated over 10s period
* complete_latency_99_9th - complete latency (ms), 99.9th percentile, calculated over 10s period
* complete_latency_max - complete latency (ms), maximum complete latency seen over 10s period

Complete latency measures the amount of time in ms that a segment,
emitted by an input task, takes to be fully acked through all tasks in the job. Onyx's
messaging model works by tracking all segments that result from operations on
the input segment at each task. Complete latency gives an excellent indication of the
end to end processing time for segments through an entire job.

Complete latencies are measured in milliseconds, and percentiles are calculated
over a 10s period. For example, complete_latency_50th of 60, means that 50
percent of segments completed over the 10 second period were fully processed
within 60ms. If an Onyx job contains a simple job with three tasks `[[:A :B] [:B :C]]`, 
this means a segment read by `:A`, was sent to `:B`, processed, and
then sent to `:C`, and processed by the output `:C`, with all generated
segments acked, within 60ms.

Reducing [`:onyx/max-pending`](http://www.onyxplatform.org/docs/cheat-sheet/latest/#catalog-entry/:onyx/max-pending)
*can* reduce the complete latency, however it will also reduce the number of
segments that are currently outstanding, which can hurt throughput. Also note,
that while it may reduce complete latencies, the messages may still be waiting
on the topic/queue/etc that the plugin is reading from, and therefore the true processing latency may be masked.

##### Retries

**Task types:** input

**Tags:**
* retry_segment_rate_1s

Retry segment rate measures total number of segments, emitted from an
input task, that have taken longer than the time specified in
[`:onyx/pending-timeout`](http://www.onyxplatform.org/docs/cheat-sheet/latest/#catalog-entry/:onyx/max-pending)
(ms) without being completed, and were re-emitted as a result.

Segments that are retried often indicate that a message was lost, potentially
because a node died. However, there is also the potential that a pipeline is
overwhelmed and unable to process the segments within `:onyx/pending-timeout`.
Generally, the correct response is to improve your pipeline's performance,
and/or reduce [`:onyx/max-pending`](http://www.onyxplatform.org/docs/cheat-sheet/latest/#catalog-entry/:onyx/max-pending)
in order to exhibit more backpressure on the pipeline.

##### Throughput

**Task types:** input, function, output

**Tags:**
* throughput_1s - total number of segments processed by the task, summed over 1s 
* throughput_10s - total number of segments processed by the task, summed over 10s 
* throughput_60s - total number of segments processed by the task, summed over 60s 

The total number of segments being read and emitted by the task, summed over
1s, or 10s, or 60s. In case of input tasks, this generally corresponds to
however many segments were read from the input source over that time period. In
the case of output tasks, this corresponds to the number of segments written.
For function tasks, it corresponds to the number of segments that are processed
by the task's `:onyx/fn`. Note that throughputs may be low because the task is
slow, but can also result from tasks that are starved. Also note that input tasks may only process up to
[`:onyx/max-pending`](http://www.onyxplatform.org/docs/cheat-sheet/latest/#catalog-entry/:onyx/max-pending)
unacked segments at a given time, and thus a low input task throughput may be a
result of backpressure resulting from max-pending and a slow pipeline.

##### Pending Messages Count

**Task types:** input

**Tags:**
* pending_messages_count - number of unacked segments that are currently being processed for this task for the job

The number of segments that have been read by an input task, and have not yet been
completely acked. This gives a good indication of whether the input medium is
able to supply segments quickly enough to have segments up to
`:onyx/max-pending`, or show that an input source is being drained quickly as it is filled 
i.e. the pipeline is processing segments as soon as they arrive on the input source.

##### Batch Latency

**Task types:** input, function, output

**Tags:**
* batch_latency_50th - batch latency (ms), 50th percentile, calculated over 10s period
* batch_latency_90th - batch latency (ms), 90th percentile, calculated over 10s period 
* batch_latency_99th - batch latency (ms), 99th percentile, calculated over 10s period
* batch_latency_99_9th - batch latency (ms), 99.9th percentile, calculated over 10s period
* batch_latency_max - batch latency (ms), maximum seen over 10s period

Batch latency measures the amount if time, in ms, that it takes a to map
[`:onyx/fn`](http://www.onyxplatform.org/docs/cheat-sheet/latest/#catalog-entry/:onyx/fn)
over a batch of segments. This is generally an indication of the performance of a task's `:onyx/fn`.

#### Using Onyx Metrics

Add these maps to your `:lifecycles` vector in the argument to `:onyx.api/submit-job`.

##### Riemann metrics

Send all metrics to a Riemann instance on a single thread. Events are buffered in a core.async channel with capacity `:riemann/buffer-capacity`, default capacity is 10,000.

First, add the clojure riemann client dependency to your project. e.g.
```clojure
[riemann-clojure-client "0.4.1"]
```

In your peer boot-up namespace:

```clojure
(:require [onyx.lifecycle.metrics.metrics]
          [onyx.lifecycle.metrics.riemann])
```

```clojure
{:lifecycle/task :all ; or :task-name for an individual task
 :lifecycle/calls :onyx.lifecycle.metrics.metrics/calls
 :metrics/buffer-capacity 10000
 :metrics/sender-fn :onyx.lifecycle.metrics.riemann/riemann-sender
 :riemann/address "192.168.1.23"
 :riemann/port 5555
 :lifecycle/doc "Instruments a task's metrics and sends via riemann"}
```

If you wish to name your job, so that metrics are scoped to a job name, rather
than a job id, include the following in your job's metadata key:

```clojure
{:workflow ...
 :lifecycles ...
 :metadata {:name "YOURJOBNAME"}}
```

##### Timbre Logging

Computes the following metrics and logs via timbre.

In your peer boot-up namespace:

```clojure
(:require [onyx.lifecycle.metrics.timbre]
          [onyx.lifecycle.metrics.metrics])
```

Add the following lifecycle.

```clojure

{:lifecycle/task :all ; or :task-name for an individual task
 :lifecycle/calls :onyx.lifecycle.metrics.metrics/calls
 :metrics/buffer-capacity 10000
 :metrics/sender-fn :onyx.lifecycle.metrics.timbre/timbre-sender
 :lifecycle/doc "Instruments a task's metrics to timbre"}
```

If you wish to name your job, so that metrics are scoped to a job name, rather
than a job id, include the following in your job's metadata key:

```clojure
{:workflow ...
 :lifecycles ...
 :metadata {:name "YOURJOBNAME"}}
```

##### DataDog metrics

Metrics for the [DataDog](https://www.datadoghq.com/) SaaS. Sends all metrics to a DogstatsD agent on a single thread.

First, add the clojure DogstatsD client dependency to your project. e.g.
```clojure
[cognician/dogstatsd-clj "0.1.2"] 
```
In your peer boot-up namespace:

```clojure
(:require [onyx.lifecycle.metrics.metrics]
          [onyx.metrics.dogstatsd])
```

```clojure
{:lifecycle/task               :all
 :lifecycle/calls              :onyx.lifecycle.metrics.metrics/calls
 :metrics/buffer-capacity      10000
 :metrics/sender-fn            :onyx.metrics.dogstatsd/dogstatsd-sender
 :dogstatsd/url                "localhost:8125"
 :dogstatsd/global-tags        ["tag1" "tag2" "tag3"] ;; optional 
 :dogstatsd/global-sample-rate 1.0 ;; optional, generally leave as 1.0 as message volume is small
 :lifecycle/doc                "Instruments a task's metrics and sends to a datadog agent"}
```

More detailed information on dogstatsd related settings can be found
[here](https://github.com/Cognician/dogstatsd-clj).

If you wish to name your job, so that metrics are scoped to a job name, rather
than a job id, include the following in your job's metadata key:

```clojure
{:workflow ...
 :lifecycles ...
 :metadata {:name "YOURJOBNAME"}}
```

##### NewRelic 

A plugin for [NewRelic](http://newrelic.com/). Sends all metrics to NewRelic via their plugin API.

First add the following dependencies to your project e.g.

```clojure
[clj-http "2.1.0"]
[cheshire "5.5.0"]
```

In your peer boot-up namespace:

```clojure
(:require [onyx.lifecycle.metrics.metrics]
          [onyx.metrics.newrelic])
```

Add a lifecycle to your job:

```clojure
{:lifecycle/task :all
 :lifecycle/calls :onyx.lifecycle.metrics.metrics/calls
 :newrelic/batch-size 100
 :newrelic/batch-timeout 200
 :metrics/sender-fn :onyx.metrics.newrelic/newrelic-sender
 :lifecycle/doc "Instruments a task's metrics to NewRelic"}
```

Please ensure you set the environment variable NEW_RELIC_LICENSE_KEY to your license
key, or set `:newrelic/license-key` in the lifecycle (not recommend as it will
be written to ZooKeeper).

If you wish to name your job, so that metrics are scoped to a job name, rather
than a job id, include the following in your job's metadata key:

```clojure
{:workflow ...
 :lifecycles ...
 :metadata {:name "YOURJOBNAME"}}
```

#### Websocket output

Sends all metric data to a websocket. Currently we do have a suggested web server implementation, as websocket inputs are no longer supported in onyx-dashboard.

In your peer boot-up namespace:

```clojure
(:require [onyx.lifecycle.metrics.websocket]
          [onyx.lifecycle.metrics.metrics])
```

Add the following lifecycle.


```clojure
{:lifecycle/task :all ; or :task-name for an individual task
 :lifecycle/calls :onyx.lifecycle.metrics.metrics/calls
 :metrics/sender-fn :onyx.lifecycle.metrics.websocket/websocket-sender
 :websocket/address "ws://127.0.0.1:3000/metrics"
 :lifecycle/doc "Instruments a task's metrics to a websocket."}
```



### Handy Tip

Sometimes, you may want a quick way to instrument all the tasks in a workflow.
This can be achieved by using :lifecycle/task :all for your given lifecycles.


## License

Copyright © 2015 Michael Drogalis

Distributed under the Eclipse Public License either version 1.0 or (at
your option) any later version.
