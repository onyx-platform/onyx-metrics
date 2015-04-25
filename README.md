# onyx-metrics

Onyx Lifecycle plugin for instrumenting workflows. Track throughput and metrics of your tasks and log the output to Timbre and/or dashboard.

#### Installation

In your project file:

```clojure
[com.mdrogalis/onyx-metrics "0.6.0-SNAPSHOT"]
```

In your peer boot-up namespace:

```clojure
(:require [onyx.lifecycle.metrics.throughput]
          [onyx.lifecycle.metrics.latency]
          [onyx.lifecycle.metrics.timbre])
```

#### Lifecycle entries

##### Throughput

```clojure
{:lifecycle/task :my-task-name
 :lifecycle/pre :onyx.lifecycle.metrics.throughput/pre
 :lifecycle/post-batch :onyx.lifecycle.metrics.throughput/post-batch
 :lifecycle/post :onyx.lifecycle.metrics.throughput/post
 :throughput/retention-ms 60000
 :lifecycle/doc "Instruments a task's throughput metrics"}
```

##### Timbre Logging

```clojure
{:lifecycle/task :my-task-name
 :lifecycle/pre :onyx.lifecycle.metrics.timbre/pre
 :lifecycle/post :onyx.lifecycle.metrics.timbre/post
 :timbre/interval-ms 2000
 :lifecycle/doc "Prints task metrics to Timbre every 2000 ms"}
```

## License

Copyright © 2015 Michael Drogalis

Distributed under the Eclipse Public License either version 1.0 or (at
your option) any later version.
