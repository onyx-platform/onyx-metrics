# onyx-metrics

Onyx Lifecycle plugin for instrumenting workflows. Track throughput and metrics of your tasks and log the output to Timbre and/or dashboard via websockets.

#### Installation

In your project file:

```clojure
[org.onyxplatform/onyx-metrics "0.9.7.0-alpha21"]
```

#### Metrics

Computes the following metrics.
* 1s, 10s, 60s throughput
* 50th, 90th, 99th, 99.9th, maxiumum percentile batch latency (time to process a batch, computed every 10s)
* Input tasks
  * 1s input segment retries (input segments that have not been processed within :onyx/pending-timeout)
  * 50th, 90th, 99th, 99.9th, maximum percentile input segment completed latency (time to full process an input segment through the DAG, computed every 10s)
  * count of pending-messages (messages outstanding) 

#### Lifecycle entries

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

Sends all metric data to a websocket. The Onyx dashboard already knows what to do with this output, but you can direct it anywhere.

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
