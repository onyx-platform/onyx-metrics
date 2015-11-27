# onyx-metrics

Onyx Lifecycle plugin for instrumenting workflows. Track throughput and metrics of your tasks and log the output to Timbre and/or dashboard via websockets.

#### Installation

In your project file:

```clojure
[org.onyxplatform/onyx-metrics "0.8.2.2-SNAPSHOT"]
```

#### Metrics

Computes the following metrics.
* 1s, 10s, 60s throughput
* 50th, 90th, 99th, 99.9th percentile batch latency (time to process a batch, computed every 10s)
* 1s input segment retries (input segments that have not been processed within :onyx/pending-timeout)
* 50th, 90th, 99th, 99.9th percentile input segment completed latency (time to full process an input segment through the DAG, computed every 10s)

#### Lifecycle entries

Add these maps to your `:lifecycles` vector in the argument to `:onyx.api/submit-job`.


##### Riemann metrics


Send all metrics to a Riemann instance on a single thread. Events are buffered in a core.async channel with capacity `:riemann/buffer-capacity`, default capacity is 10,000.

First, add the clojure riemann client dependency to your project. e.g.
```clojure
[riemann-clojure-client "0.4.1"]


In your peer boot-up namespace:

```clojure
(:require [onyx.lifecycle.metrics.metrics]
          [onyx.lifecycle.metrics.riemann])
```

```clojure

{:lifecycle/task :all ; or :task-name for an individual task
 :lifecycle/calls :onyx.lifecycle.metrics.metrics/calls
 :metrics/buffer-capacity 10000
 :metrics/workflow-name "your-workflow-name"
 :metrics/sender-fn :onyx.lifecycle.metrics.riemann/riemann-sender
 :riemann/address "192.168.1.23"
 :riemann/port 5555
 :lifecycle/doc "Instruments a task's metrics and sends via riemann"}

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
 :metrics/workflow-name "your-workflow-name"
 :metrics/sender-fn :onyx.lifecycle.metrics.timbre/timbre-sender
 :lifecycle/doc "Instruments a task's metrics to timbre"}
```

#### Websocket output

Sends all metric data to a websocket. The Onyx dashboard already knows what to do with this output, but you can direct it anywhere.

In your peer boot-up namespace:

```clojure
(:require [onyx.lifecycle.metrics.websocket]
          [onyx.lifecycle.metrics.metrics])
```

Add the following lifecycle.


```clojure
{:lifecycle/task :all ; or :task-name for an individual task
 :lifecycle/calls :onyx.lifecycle.metrics.websocket/calls
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
