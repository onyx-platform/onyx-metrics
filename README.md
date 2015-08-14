# onyx-metrics

Onyx Lifecycle plugin for instrumenting workflows. Track throughput and metrics of your tasks and log the output to Timbre and/or dashboard via websockets.

#### Installation

In your project file:

```clojure
[org.onyxplatform/onyx-metrics "0.7.0"]
```

In your peer boot-up namespace:

```clojure
(:require [onyx.lifecycle.metrics.throughput]
          [onyx.lifecycle.metrics.latency]
          [onyx.lifecycle.metrics.timbre]
          [onyx.lifecycle.metrics.websocket]
          [onyx.lifecycle.metrics.riemann])
```

#### Lifecycle entries

Add these maps to your `:lifecycles` vector in the argument to `:onyx.api/submit-job`.

##### Throughput

Computes throughput in terms of segments per second for 10s, 30s, and 60s windows.

```clojure
{:lifecycle/task :my-task-name
 :lifecycle/calls :onyx.lifecycle.metrics.throughput/calls
 :throughput/retention-ms 60000
 :lifecycle/doc "Instruments a task's throughput metrics"}
```

##### Batch Latency

Computes the 50th, 90th, and 99th percentile latency in milliseconds per batch of segments for 10s, 30s, and 60s windows.

```clojure
{:lifecycle/task :my-task-name
 :lifecycle/calls :onyx.lifecycle.metrics.latency/calls
 :latency/retention-ms 60000
 :lifecycle/doc "Instruments a task's latency metrics per batch"}
```

##### Timbre Logging

Logs all statistics collected to Timbre.

```clojure
{:lifecycle/task :my-task-name
 :lifecycle/calls :onyx.lifecycle.metrics.timbre/calls
 :timbre/interval-ms 2000
 :lifecycle/doc "Prints task metrics to Timbre every 2000 ms"}
```

#### Websocket output

Sends all metric data to a websocket. The Onyx dashboard already knows what to do with this output, but you can direct it anywhere.

```clojure
{:lifecycle/task task
 :lifecycle/calls :onyx.lifecycle.metrics.websocket/calls
 :websocket/address "ws://127.0.0.1:3000/metrics"
 :websocket/interval-ms 2000
 :lifecycle/doc "Sends metric data to a websocket."}
```

#### Riemann output

Send all metrics to a Riemann instance.

```clojure
{:lifecycle/task task
 :lifecycle/calls :onyx.lifecycle.metrics.riemann/calls
 :riemann/workflow-name workflow-name ;; An extra tag for riemann, in order to namespace multiple running Onyx jobs.
 :riemann/address "192.168.99.100"
 :riemann/port 5555
 :riemann/interval-ms 1000}
```
Sometimes, you may want a quick way to instrument all the tasks in a workflow.
This can be achieved using something like this.

```clojure

(defn add-metrics [lifecycle workflow retention-ms reporting-ms riemann-hostname riemann-port riemann-name]
  (let [tasks (distinct (flatten denver-health-workflow))]
    (-> (reduce (fn [acc x]
      (conj acc
            {:lifecycle/task x
             :lifecycle/calls :onyx.lifecycle.metrics.throughput/calls
             :throughput/retention-ms (or retention-ms 60000)}

            {:lifecycle/task x
             :lifecycle/calls :onyx.lifecycle.metrics.latency/calls
             :latency/retention-ms (or retention-ms 60000)}

            {:lifecycle/task x
             :lifecycle/calls :onyx.lifecycle.metrics.riemann/calls
             :riemann/interval-ms (or reporting-ms 1000)
             :riemann/workflow-name riemann-name
             :riemann/address riemann-hostname
	     :riemann/port riemann-port})) [] tasks)
       (concat lifecycle))))
```

## License

Copyright © 2015 Michael Drogalis

Distributed under the Eclipse Public License either version 1.0 or (at
your option) any later version.
