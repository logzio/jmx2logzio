# jmx2logzio

jmx2logzio is a lightweight tool for polling JMX metrics and sending them to Logz.io.

This doc shows you how to set up jmx2logzio.
You have two options here:

* Run as a [Java agent](#as-a-java-agent-config) (to get metrics directly from the MBean platform)
* Run as an [app that connects to a Jolokia agent](#with-a-jolokia-agent-config)

### How are metrics reported?

Metrics are reported as
`[SERVICE-NAME].[SERVICE-HOST].[METRIC-NAME]`.

<h2 id="as-a-java-agent-config">jmx2logzio as Java agent setup</h2>

In most cases, you can configure jmx2logzio to run as an agent.
In this configuration, jmx2logzio sends metrics to Logz.io at user-defined intervals.

**Note**: The agent uses Slf4j logging framework.
To get logs from the agent, you'll need to supply a [slf4j binder](https://www.slf4j.org/faq.html#requirements).

### Configuration

**You'll need**:
[Java 1.8](https://www.oracle.com/technetwork/java/javase/downloads/jdk8-downloads-2133151.html) or higher

#### 1.  Download jmx2logzio

Download jmx2logzio.jar to your current folder.
You'll use this in step 2.

```shell
RELEASE_JAR=$(curl -s https://api.github.com/repos/logzio/jmx2logzio/releases/latest \
| grep "browser_download_url" | awk '{print substr($2, 2, length($2)-2)}') \
; wget -O jmx2logzio-javaagent.jar $RELEASE_JAR
```

#### 2.  Configure and run jmx2logzio

Run your Java app,
adding `-javaagent:path/to/jmx2logzio/jar/file.jar` and configuration arguments to the command.
Include extra configuration arguments as KEY=VALUE, with a comma between each argument.

This code block shows a sample command to run jmx2logzio with runtime configuration.
For a complete list of options, see the configuration parameters below the code block.👇

```shell
java -javaagent:./jmx2logzio-javaagent.jar=LOGZIO_TOKEN=<<SHIPPING-TOKEN>>,SERVICE_NAME=myService /path/to/your/app
```

**Parameters**

| Parameter | Description |
|---|---|
| LOGZIO_TOKEN | **Required**. Your Logz.io [account token](https://app.logz.io/#/dashboard/settings/manage-accounts) <br> Replace `<<SHIPPING-TOKEN>>` with the [token](https://app.logz.io/#/dashboard/settings/general) of the account you want to ship to. |
| SERVICE_NAME | **Required**. A name you define for the service.This is included in the reported metrics. |
| LISTENER_URL | **Default**: `https://listener.logz.io:8071` <br>  Listener URL and port.Replace `<<LISTENER-HOST>>` with your region's listener host (for example `listner.logz.io`). For more information on finding your account's region, see [Account region](https://docs.logz.io/user-guide/accounts/account-region.html). |
| SERVICE_HOST | Hostname to be included in the reported metrics. | **Default**: Host machine name |
| POLLING_INTERVAL_IN_SEC | **Default**: `30` <br>  Metrics polling interval, in seconds. |
| WHITE_LIST_REGEX | **Default**: `.*` (match everything) <br> Only metrics matching this regex will be sent. |
| BLACK_LIST_REGEX | **Default**: `$a` (match nothing) <br> Metrics matching this regex will not be sent. |
| EXTRA_DIMENSIONS | A list of key-values separated by `:` that will be added to the dimensions of the collected metrics. <br> Example: `EXTRA_DIMENSIONS={origin=local:env=java}` |
| FROM_DISK | **Default**: `true` <br> If `true`, metrics are stored on disk until they're shipped (see [If FROM_DISK=true](#agent-if-fromdisk-true)). If `false`, metrics persist in memory until they're shipped (see [If FROM_DISK=false](#agent-if-fromdisk-false)). |

<span id="agent-if-fromdisk-true">**If FROM_DISK=true**</span>

| Parameter | Description |
|---|---|
| QUEUE_DIR | **Default**: `./metrics` <br> Path to store the queue.Must be a path to an existing folder. |
| FILE_SYSTEM_SPACE_LIMIT | **Default**: `98` <br> Threshold percentage of disk space at which to stop queueing. If this threshold is reached, all new metrics are dropped until used space drops below the threshold. <br> Set to `-1` to ignore threshold. |
| DISK_SPACE_CHECKS_INTERVAL | **Default**: `1000` <br> Time interval, in milliseconds, to check for disk space. |
| CLEAN_SENT_METRICS_INTERVAL | **Default**: `30` <br>  Time interval, in seconds, to clean sent metrics from the disk. |

<span id="agent-if-fromdisk-false">**If FROM_DISK=false**</span>

| Parameter | Description |
|---|---|
| IN_MEMORY_QUEUE_CAPACITY | **Default**: `1024 * 1024 * 100` <br>  The amount of memory, in bytes, jmx2logzio can use for the memory queue.Set to `-1` for unlimited bytes. |
| LOGS_COUNT_LIMIT | **Default**: `-1` <br> The number of logs in the memory queue before dropping new logs. If set to `-1`, the sender won't limit the queue by log count. |

#### 3.  Check Logz.io for your metrics

Give your metrics some time to get from your system to ours, and then open [Logz.io](https://app.logz.io/#/dashboard/kibana).

<h2 id="with-a-jolokia-agent-config">jmx2logzio + Jolokia agent setup</h2>

If you want to poll metrics from a remote source, you can use Jolokia agent + Jmx2logzio.
In this configuration, Jolokia exposes the metrics through an API, which jmx2logzio reads and sends to Logz.io.
In this case, jmx2logzio can [run as a docker]( #jmx2logzio-in-a-docker).

### Configuration

**You'll need**:
[Java 1.8](https://www.oracle.com/technetwork/java/javase/downloads/jdk8-downloads-2133151.html) or higher

#### 1.  Run the Jolokia agent

Download the [Jolokia JVM Agent JAR file](http://search.maven.org/remotecontent?filepath=org/jolokia/jolokia-jvm/1.6.0/jolokia-jvm-1.6.0-agent.jar).

Run your Java app, adding `-javaagent:path/to/jolokia/jar/file.jar` to the command.
For example:

```shell
java -javaagent:/opt/jolokia/jolokia-jvm-1.6.0-agent.jar /path/to/your/app
```

**Note**: You can specify a custom configuration for Jolokia agent at runtime.
For more information, see [Jolokia as JVM Agent](https://jolokia.org/reference/html/agents.html#jvm-agent) from Jolokia.

#### 2.  Download and configure jmx2logzio

```shell
RELEASE_JAR=$(curl -s https://api.github.com/repos/logzio/jmx2logzio/releases/latest \
| grep "browser_download_url" | awk '{print substr($2, 2, length($2)-2)}') \
; wget -O jmx2logzio-javaagent.jar $RELEASE_JAR
```

Create a configuration file specifying the parameters below.
For help, see our [example configuration file](https://raw.githubusercontent.com/logzio/jmx2logzio/master/config.conf).

**Parameters**

| Parameter | Description |
|---|---|
| service.name | **Required**. A name you define for the service. This is included in the reported metrics. |
| service.host | **Default**: Host machine name _(if not defined in application.conf)_ <br> Hostname to be included in the reported metrics. |
| service.poller.white-list-regex | **Default**: `.*` _(match everything)_ <br>  Only metrics matching this regex will be sent. |
| service.poller.black-list-regex | **Default**: `$a` _(match nothing)_ <br> Metrics matching this regex will not be sent. |
| service.poller.jolokia.jolokiaFullUrl | URL of the remote Jolokia agent you're forwarding metrics to. |
| service.poller.metrics-polling-interval-in-seconds | **Default**: `30` <br> Metrics polling interval, in seconds. |
| extra-dimensions | A dictionary of key-values that will be added to the dimensions of the collected metrics. |
| logzio-java-sender.url | **Default**: `https://listener.logz.io:8071` <br> Listener URL and port. <br> For more information on finding your account's region, see [Account region]({{site.baseurl}}/user-guide/accounts/account-region.html). |
| logzio-java-sender.token | **Required**. Your Logz.io [account token](https://app.logz.io/#/dashboard/settings/manage-accounts) |
| logzio-java-sender.from-disk | **Default**: `true` <br> If `true`, metrics are stored on disk until they're shipped (see [If from-disk=true](#jolokia-if-fromdisk-true)). If `false`, metrics persist in memory until they're shipped (see [If from-disk=false](#jolokia-if-fromdisk-false)). |

<span id="jolokia-if-fromdisk-true">**If from-disk=true**</span>

| Parameter | Description |
|---|---|
| logzio-java-sender.queue-dir | **Default**: `metrics` <br> Path to store the queue. Must be a path to an existing folder. |
| logzio-java-sender.file-system-full-percent-threshold | **Default**: `98` <br>  Threshold percentage of disk space at which to stop queueing. If this threshold is reached, all new metrics are dropped until used space drops below the threshold. Set to `-1` to ignore threshold. |
| logzio-java-sender.clean-sent-metrics-interval | **Default**: `30` <br> Time interval, in seconds, to clean sent metrics from the disk. |
| logzio-java-sender.disk-space-checks-interval | **Default**: `1000` <br> Time interval, in milliseconds, to check for disk space. |

<span id="jolokia-if-fromdisk-false">**If from-disk=false**</span>

| Parameter | Description |
|---|---|
| logzio-java-sender.in-memory-queue-capacity | **Default**: `1024 * 1024 * 100` <br> The amount of memory, in bytes, jmx2logzio can use for the memory queue. Set to `-1` for unlimited bytes. |
| logzio-java-sender.log-count-limit | **Default**: `-1` <br> The number of logs in the memory queue before dropping new logs. Default value is -1 (the sender will not limit the queue by logs count). |

#### 3.  Run jmx2logzio

Make sure your app is running with the Jolokia agent, and then start jmx2logzio.

You can launch jmx2logzio.jar as a standalone jar or in a Docker container.

As a jar:

```shell
java -jar jmx2logzio.jar config.conf
```

In a container:

```shell
docker pull logzio/jmx2logzio
docker run -v $(pwd)/config.conf:/application.conf logzio/jmx2logzio
```

#### 4.  Check Logz.io for your metrics

Give your metrics some time to get from your system to ours, and then open [Logz.io](https://app.logz.io/#/dashboard/kibana).

## Changelog
- v1.0.10
    - Use newer version of logzio-java-sender to prevent tokens clash.
- v1.0.9
    - Provide an external configuration file when using as an app that connects to a Jolokia agent
- v1.0.8
    - Hide your logz.io token, to keep you safe
    - Change logger logic for 'instance not found'
- v1.0.7
    - For testing purposes and simulations we'd like the agent to be able to work with local addresses
- v1.0.6
    - This release ensures no conflicts between the dependencies of this agent and any java app.
- v1.0.5
    - Kill sender threads upon shutdown
- v1.0.4
    - Jmx2logz.io logger changed from logback to slf4j and will now inhere its properties from the java app.
    - Set level feature was removed and will also be inherited from the main app
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
