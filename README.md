# jmx2logzio

jmx2logzio is a lightweight tool for polling JMX metrics and sending them to Logz.io.

You can set up jmx2logzio to run either as a [Java agent](#jmx2logzio-as-java-agent-setup) (to get metrics directly from the MBean platform) or as an [independent app that to a Jolokia agent](#jmx2logzio-jolokia-setup).

#### How are metrics reported?

Metrics reported by jmx2logzio follow this format:

[SERVICE-NAME].[SERVICE-HOST].[METRIC-NAME]

## jmx2logzio as Java agent setup {#jmx2logzio-as-java-agent-setup}

In most cases, you can configure jmx2logzio to run as an agent.
In this configuration, jmx2logzio forwards metrics directly to Logz.io.

If your app is running in a Docker container, consider using [jmx2logzio with Jolokia](#jmx2logzio-jolokia-setup) instead.

**You'll need**: [Maven](https://maven.apache.org/), [Java 1.8](https://www.oracle.com/technetwork/java/javase/downloads/jdk8-downloads-2133151.html) or higher

### 1. Download and build jmx2logzio

Clone the jmx2logzio GitHub repo to your machine.

```shell
git clone https://github.com/logzio/jmx2logzio.git
```

`cd` to the jmx2logzio/ folder and build the source:

```shell
mvn clean install
```

### 2. Configure and run jmx2logzio

You'll find the jmx2logzio jar file in the jmx2logzio/target/ folder.

Run your Java app, adding `-javaagent:path/to/jmx2logzio/jar/file.jar` and configuration arguments to the command.
Include extra configuration arguments as KEY=VALUE, with a comma between each argument.

For a complete list of options, see the configuration parameters below the code block.👇

For example:

 ```shell
 java -javaagent:~/jmx2logzio/target/jmx2logzio-1.0.1.jar=LOGZIO_TOKEN=<ACCOUNT-TOKEN>,SERVICE_NAME=myService /path/to/your/app
 ```

#### Parameters

| Parameter | Description |
|---|---|
| **LOGZIO_TOKEN** | **Required**. Your Logz.io [account token](https://app.logz.io/#/dashboard/settings/manage-accounts) |
| **SERVICE_NAME** | **Required**. A name you define for the service. This is included in the reported metrics. |
| **LISTENER_URL** | Listener URL and port. <br /> If your login URL is app.logz.io, use `listener.logz.io`. If your login URL is app-eu.logz.io, use `listener-eu.logz.io`. <br /> **Default**: `https://listener.logz.io:8071` |
| **SERVICE_HOST** | Hostname to be included in the reported metrics. <br /> **Default**: Host machine name |
| **POLLING_INTERVAL_IN_SEC** | Metrics polling interval, in seconds. <br /> **Default**: `30` |
| **WHITE_LIST_REGEX** | Only metrics matching this regex will be sent. <br /> **Default**: `.*` (match everything) |
| **BLACK_LIST_REGEX** | Metrics matching this regex will not be sent. <br /> **Default**: `$a` (match nothing) |
| **FROM_DISK** | If `true`, metrics are stored on disk until they're shipped (see [If FROM_DISK=true](#agent-if-fromdisk-true)). If `false`, metrics persist in memory until they're shipped (see see [If FROM_DISK=false](#agent-if-fromdisk-false)). <br /> **Default**: `true` |

#### If FROM_DISK=true {#agent-if-fromdisk-true}

| Parameter | Description |
|---|---|
| **QUEUE_DIR** | Path to store the queue. Must be a path to an existing folder. <br /> **Default** `./metrics` |
| **FILE_SYSTEM_SPACE_LIMIT** | Threshold percentage of disk space at which to stop queueing. If this threshold is reached, all new metrics are dropped until used space drops below the threshold. Set to `-1` to ignore threshold. <br /> **Default**: `98` |
| **DISK_SPACE_CHECKS_INTERVAL** | Time interval, in milliseconds, to check for disk space <br /> **Default**: `1000` |
| **CLEAN_SENT_METRICS_INTERVAL** | Time interval, in seconds, to clean sent metrics from the disk <br /> **Default**: `30` |

#### If FROM_DISK=false {#agent-if-fromdisk-false}

| Parameter | Description |
|---|---|
| **IN_MEMORY_QUEUE_CAPACITY** | The amount of memory, in bytes, jmx2logzio can use for the memory queue. Set to `-1` for unlimited bytes. <br /> **Default**: `1024 * 1024 * 100` |
| **LOGS_COUNT_LIMIT** | The number of logs in the memory queue before dropping new logs. Default value is -1 (the sender will not limit the queue by logs count) <br /> **Default:** `-1` |


## jmx2logzio + Jolokia setup _(recommended)_ {#jmx2logzio-jolokia-setup}

If you experience issues such as port conflicts for Docker containers, you can run a Jolokia agent to act as a go-between for sending metrics from your app to Logz.io.
In this configuration, jmx2logzio forwards metrics to Jolokia, which sends those metrics to Logz.io.

**You'll need**: [Maven](https://maven.apache.org/), [Java 1.8](https://www.oracle.com/technetwork/java/javase/downloads/jdk8-downloads-2133151.html) or higher

### 1. Run the Jolokia agent

Download the [Jolokia JVM Agent JAR file](http://search.maven.org/remotecontent?filepath=org/jolokia/jolokia-jvm/1.6.0/jolokia-jvm-1.6.0-agent.jar).

Run your Java app, adding `-javaagent:path/to/jolokia/jar/file.jar` to the command.

For example:

 ```shell
 java -javaagent:/opt/jolokia/jolokia-jvm-1.6.0-agent.jar /path/to/your/app
 ```

**Note**: You can specify a custom configuration for Jolokia agent at runtime. For more information, see [Jolokia as JVM Agent](https://jolokia.org/reference/html/agents.html#jvm-agent) from Jolokia.


### 2. Download and configure jmx2logzio

Clone the jmx2logzio GitHub repo to your machine.

```shell
git clone https://github.com/logzio/jmx2logzio.git
```

Open jmx2logzio/src/main/resources/application.config in a text editor, and set the parameters below.

**Note**: All parameters are required, but we've provided default settings for most of them in the config file.

#### Parameters

| Parameter | Description |
|---|---|
| **service.name** | A name you define for the service. This is included in the reported metrics. |
| **service.host** | Hostname to be included in the reported metrics. <br /> **Default**: Host machine name (if setting not defined in application.conf) |
| **service.poller.white-list-regex** | Only metrics matching this regex will be sent. <br /> **Default**: `.*` (match everything) |
| **service.poller.black-list-regex** | Metrics matching this regex will not be sent. <br /> **Default**: `$a` (match nothing) |
| **service.poller.jolokia.jolokiaFullUrl** | URL of the remote Jolokia agent you're forwarding metrics to. |
| **metricsPollingIntervalInSeconds** | Metrics polling interval, in seconds. <br /> **Default**: `30` |
| **logzioJavaSender.url** | Listener URL and port. <br /> If your login URL is app.logz.io, use `listener.logz.io`. If your login URL is app-eu.logz.io, use `listener-eu.logz.io`. <br /> **Default**: `https://listener.logz.io:8071` |
| **logzioJavaSender.token** | **Required**. Your Logz.io [account token](https://app.logz.io/#/dashboard/settings/manage-accounts) |
| **logzioJavaSender.from-disk** | If `true`, metrics are stored on disk until they're shipped (see [If from-disk=true](#jolokia-if-fromdisk-true)). If `false`, metrics persist in memory until they're shipped (see see [If from-disk=false](#jolokia-if-fromdisk-false)). <br /> **Default**: `true` |

#### If from-disk=true {#jolokia-if-fromdisk-true}

| Parameter | Description |
|---|---|
| **logzioJavaSender.queue-dir** | Path to store the queue. Must be a path to an existing folder. <br /> **Default** `metrics` |
| **logzioJavaSender.file-system-full-percent-threshold** | Threshold percentage of disk space at which to stop queueing. If this threshold is reached, all new metrics are dropped until used space drops below the threshold. Set to `-1` to ignore threshold. <br /> **Default**: `98` |
| **logzioJavaSender.clean-sent-metrics-interval** | Time interval, in seconds, to clean sent metrics from the disk <br /> **Default**: `30` |
| **logzioJavaSender.disk-space-checks-interval** | Time interval, in milliseconds, to check for disk space <br /> **Default**: `1000` |

#### If from-disk=false {#jolokia-if-fromdisk-false}

| Parameter | Description |
|---|---|
| **logzioJavaSender.in-memory-queue-capacity** | The amount of memory, in bytes, jmx2logzio can use for the memory queue. Set to `-1` for unlimited bytes. <br /> **Default**: `1024 * 1024 * 100` |
| **logzioJavaSender.log-count-limit** | The number of logs in the memory queue before dropping new logs. Default value is -1 (the sender will not limit the queue by logs count) <br /> **Default:** `-1` |


### 3. Build and run jmx2logzio

`cd` to the jmx2logzio/ folder and build the source:

```shell
mvn clean install
```

Make sure your app is running, and then start jmx2logzio.

```shell
java -jar jmx2logzio.jar
```

You'll find the jmx2logzio jar file in the jmx2logzio/target/ folder.


# The other stuff

## Contributing

We welcome any contribution! Here's how you can help:
- Open an issue (Bug, Feature request, etc)
- Pull requests for any additional functionality

## License

See [LICENSE](LICENSE.md)