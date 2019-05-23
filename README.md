# jmx2logzio

jmx2logzio is a lightweight tool for polling JMX metrics and sending them to Logz.io.

This doc shows you how to set up jmx2logzio.
You have two options here:

* Run as a [Java agent](#jmx2logzio-as-java-agent-setup) (to get metrics directly from the MBean platform)
* Run as an [app that connects to a Jolokia agent](#jmx2logzio-jolokia-setup)

#### How are metrics reported?

Metrics reported by jmx2logzio follow this format:

[SERVICE-NAME].[SERVICE-HOST].[METRIC-NAME]


<h2 #id="jmx2logzio-as-java-agent-setup">jmx2logzio as Java agent setup</h2>

In most cases, you can configure jmx2logzio to run as an agent.
In this configuration, jmx2logzio forwards metrics directly to Logz.io.

If your app is running in a Docker container, consider using [jmx2logzio with Jolokia](#jmx2logzio-jolokia-setup) instead.

**Note:** The agent uses Slf4j logging framework. In order to get logs from the agent, you'll need to supply a [slf4j binder](https://www.slf4j.org/faq.html#requirements).

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

This code block shows a sample command to run jmx2logzio with runtime configuration.
For a complete list of options, see the configuration parameters below the code block.👇

```shell
java -javaagent:~/jmx2logzio/target/jmx2logzio-1.0.4-javaagent.jar=LOGZIO_TOKEN=<ACCOUNT-TOKEN>,SERVICE_NAME=myService /path/to/your/app
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
| **EXTRA_DIMENSIONS** | A list of key-values separated by ':' that will be added to the dimensions of the collected metrics. <br /> Example: EXTRA_DIMENSIONS={origin=local:env=java} |
| **FROM_DISK** | If `true`, metrics are stored on disk until they're shipped (see [If FROM_DISK=true](#agent-if-fromdisk-true)). If `false`, metrics persist in memory until they're shipped (see [If FROM_DISK=false](#agent-if-fromdisk-false)). <br /> **Default**: `true` |

<h4 id="agent-if-fromdisk-true">If FROM_DISK=true</h4>

| Parameter | Description |
|---|---|
| **QUEUE_DIR** | Path to store the queue. Must be a path to an existing folder. <br /> **Default** `./metrics` |
| **FILE_SYSTEM_SPACE_LIMIT** | Threshold percentage of disk space at which to stop queueing. If this threshold is reached, all new metrics are dropped until used space drops below the threshold. Set to `-1` to ignore threshold. <br /> **Default**: `98` |
| **DISK_SPACE_CHECKS_INTERVAL** | Time interval, in milliseconds, to check for disk space <br /> **Default**: `1000` |
| **CLEAN_SENT_METRICS_INTERVAL** | Time interval, in seconds, to clean sent metrics from the disk <br /> **Default**: `30` |

<h4 id="agent-if-fromdisk-false">If FROM_DISK=false</h4>

| Parameter | Description |
|---|---|
| **IN_MEMORY_QUEUE_CAPACITY** | The amount of memory, in bytes, jmx2logzio can use for the memory queue. Set to `-1` for unlimited bytes. <br /> **Default**: `1024 * 1024 * 100` |
| **LOGS_COUNT_LIMIT** | The number of logs in the memory queue before dropping new logs. Default value is -1 (the sender will not limit the queue by logs count) <br /> **Default:** `-1` |

<h2 id="jmx2logzio-jolokia-setup">jmx2logzio + Jolokia setup</h2>

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

#### Parameters

| Parameter | Description |
|---|---|
| **service.name** |  **Required**. A name you define for the service. This is included in the reported metrics. |
| **service.host** | Hostname to be included in the reported metrics. <br /> **Default**: Host machine name (if setting not defined in application.conf) |
| **service.poller.white-list-regex** | Only metrics matching this regex will be sent. <br /> **Default**: `.*` (match everything) |
| **service.poller.black-list-regex** | Metrics matching this regex will not be sent. <br /> **Default**: `$a` (match nothing) |
| **service.poller.jolokia.jolokiaFullUrl** | URL of the remote Jolokia agent you're forwarding metrics to. |
| **service.poller.metrics-polling-interval-in-seconds** | Metrics polling interval, in seconds. <br /> **Default**: `30` |
| **extra-dimensions** | A dictionary of key-values that will be added to the dimensions of the collected metrics |
| **logzio-java-sender.url** | Listener URL and port. <br /> If your login URL is app.logz.io, use `listener.logz.io`. If your login URL is app-eu.logz.io, use `listener-eu.logz.io`. <br /> **Default**: `https://listener.logz.io:8071` |
| **logzio-java-sender.token** | **Required**. Your Logz.io [account token](https://app.logz.io/#/dashboard/settings/manage-accounts) |
| **logzio-java-sender.from-disk** | If `true`, metrics are stored on disk until they're shipped (see [If from-disk=true](#jolokia-if-fromdisk-true)). If `false`, metrics persist in memory until they're shipped (see [If from-disk=false](#jolokia-if-fromdisk-false)). <br /> **Default**: `true` |

<h4 id="jolokia-if-fromdisk-true">If from-disk=true</h4>

| Parameter | Description |
|---|---|
| **logzio-java-sender.queue-dir** | Path to store the queue. Must be a path to an existing folder. <br /> **Default** `metrics` |
| **logzio-java-sender.file-system-full-percent-threshold** | Threshold percentage of disk space at which to stop queueing. If this threshold is reached, all new metrics are dropped until used space drops below the threshold. Set to `-1` to ignore threshold. <br /> **Default**: `98` |
| **logzio-java-sender.clean-sent-metrics-interval** | Time interval, in seconds, to clean sent metrics from the disk <br /> **Default**: `30` |
| **logzio-java-sender.disk-space-checks-interval** | Time interval, in milliseconds, to check for disk space <br /> **Default**: `1000` |

<h4 id="jolokia-if-fromdisk-false">If from-disk=false</h4>

| Parameter | Description |
|---|---|
| **logzio-java-sender.in-memory-queue-capacity** | The amount of memory, in bytes, jmx2logzio can use for the memory queue. Set to `-1` for unlimited bytes. <br /> **Default**: `1024 * 1024 * 100` |
| **logzio-java-sender.log-count-limit** | The number of logs in the memory queue before dropping new logs. Default value is -1 (the sender will not limit the queue by logs count) <br /> **Default:** `-1` |


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

## Changelog
- v1.0.4
    - Jmx2logz.io logger changed from logback to slf4j and will now inhere its properties from the java app.
    - Set level feature was removed and will also be inherited from the main ap
- v1.0.2
    - Shade all dependencies
- v1.0.1
    - Jmx2logz.io output log level can be configured.
- v1.0.0
    - Added Javadoc
- v0.0.8
    - Additional metric information will be sent under "dim" instead of "dimensions"
- v0.0.7
    - Added a feature that allows you to add extra dimensions to the collected metrics before sent.

# The other stuff

## Contributing

We welcome any contribution! Here's how you can help:
- Open an issue (Bug, Feature request, etc)
- Pull requests for any additional functionality

## License

See [LICENSE](LICENSE.md)
