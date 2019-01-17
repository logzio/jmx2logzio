# jmx2logzio

jmx2logzio is a one liner tool for polling JMX and send it to logz.io (every 30 seconds by default). You can install & run it on every machine you want to poll its JMX.

jmx2logzio run as a java agent, and get metrics directly from MBean Platform

The metrics reported have the following names template:

[service-name].[service-host].[metric-name]

- service-name is a parameter you supply when you run jmx2logzio. For example "LogReceiever", or "FooBackend"
- service-host is also a parameter you supply when you run jmx2logzio. If not supplied it's the hostname of the
  jolokia URL. For example: "172_1_1_2" or "log-receiver-1_foo_com"
- metric-name the name of the metric taken when polling Jolokia. For example: java_lang.type_Memory.HeapMemoryUsage.used

# Build

Build the Java Agent (you need to have a working Maven in your classpath)
```
mvn clean compile assembly:single 
```
# How to run?

This lib can get metrics from MBean Platform as well as from Jolokia. In order to use MBean Platform, we need to run inside the JVM.
- First, build the project with Maven in order to get the java agent jar (see Build above).
- Modify your app JVM arguments and add the following:  java -javaagent:/path/to/jmx2logzio-1.0-SNAPSHOT.jar=LOGZIO_TOKEN=<your_logzio_token>,SERVICE_NAME=myService ... (full parameter list below)
- The parameters are key-value pairs, in the format of key=value,key=value...
- The parameters names and functions are exactly as described in parameters section.
- For example: java -javaagent:/path/to/jmx2logzio-1.0-SNAPSHOT.jar=LOGZIO_TOKEN=<your_logzio_token>,SERVICE_NAME=myService myApp
- By default, the Jar file will be located under /target after you build it using Maven.


# How to expose JMX Metrics using Jolokia Agent

1. Download Jolokia JVM Agent JAR file [here](http://search.maven.org/remotecontent?filepath=org/jolokia/jolokia-jvm/1.6.0/jolokia-jvm-1.6.0-agent.jar).
2. Add the following command line option to your line running java:
 ```
 -javaagent:path-to-jolokia-jar-file.jar
 ```
 For example:
 ```
 -javaagent:/opt/jolokia/jolokia-jvm-1.6.0-agent.jar
 ```

By default it exposes an HTTP REST interface on port 8778. See [here](https://jolokia.org/reference/html/agents.html#jvm-agent) if you want to change it and configure it more.
We run all of ours apps using Docker, so to avoid clashes when we map the 8778 port to a unique external port belonging only to this application.

### Required parameters
| Parameter          |  Explained  |
| ------------------ |  ----- |
| **LOGZIO_TOKEN**    | Your Logz.io token, which can be found under "settings" in your account |
| **SERVICE_NAME**    | The name of the service (it's role) |

### Optional environment parameters
| Parameter          | Default                              | Explained  |
| ------------------ | ------------------------------------ | ----- |
| **LISTENER_URL**          | *https://listener.logz.io:8071*           | Logz.io URL, that can be found under "Log Shipping -> Libraries" in your account.
| **SERVICE_HOST**          | By default will be taken from the machine's hostname          | The host under which the metrics will be registered |
| **POLLING_INTERVAL_IN_SEC**          | *30*       | Metrics polling interval in seconds   |
| **WHITE_LIST_REGEX**        | .* (Match everything)    | Only metrics matching this regex will be sent    |
| **BLACK_LIST_REGEX**        | $a (Match nothing)      | Metrics matching this regex will **not** be sent     |
| **FROM_DISK**         | true      | Where the metrics will be stored until they will be sent. |

#### Parameters for in-memory queue
| Parameter          | Default                              | Explained  |
| ------------------ | ------------------------------------ | ----- |
| **IN_MEMORY_QUEUE_CAPACITY**       | *1024 * 1024 * 100*                                | The amount of memory(bytes) we are allowed to use for the memory queue. If the value is -1 the sender will not limit the queue size.|
| **LOGS_COUNT_LIMIT**       | *-1*                                | The number of logs in the memory queue before dropping new logs. Default value is -1 (the sender will not limit the queue by logs count)|


#### Parameters for disk queue
| Parameter          | Default                              | Explained  |
| ------------------ | ------------------------------------ | ----- |
| **QUEUE_DIR**          | *./metrics*                                   | Where the sender should store the queue. It should be at least one folder in path.|
| **FILE_SYSTEM_SPACE_LIMIT** | *98*                                   | The percent of used file system space at which the sender will stop queueing. When we will reach that percentage, the file system in which the queue is stored will drop all new logs until the percentage of used space drops below that threshold. Set to -1 to never stop processing new logs |
| **CLEAN_SENT_METRICS_INTERVAL**       | *30*                                    | How often should the disk queue clean sent metrics from disk |
| **DISK_SPACE_CHECKS_INTERVAL**       | *1000*                                | How often should the disk queue check for space (in milliseconds) |

## Why Jolokia?
- When running JVM application inside docker it is sometime quite complex getting JMX to work, especially around ports.
- Composing JMX URI seems very complicated and not intuitive. Jolokia REST endpoint is straight forward.
- Can catch reading several MBeans into one call (not using this feature yet though)

# Contributing

We welcome any contribution! You can help in the following way:
- Open an issue (Bug, Feature request, etc)
- Pull requests for any addition you can think of

#License

See the LICENSE file for license rights and limitations (MIT).
