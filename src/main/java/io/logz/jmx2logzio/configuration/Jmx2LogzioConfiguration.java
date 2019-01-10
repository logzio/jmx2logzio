package io.logz.jmx2logzio.configuration;

import com.typesafe.config.Config;
import io.logz.jmx2logzio.exceptions.IllegalConfiguration;
//import org.apache.kafka.clients.producer.ProducerConfig;
//import org.apache.kafka.common.serialization.StringSerializer;
import io.logz.jmx2logzio.objects.LogzioJavaSenderParams;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.net.InetAddress;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.UnknownHostException;
import java.util.regex.Pattern;

public class Jmx2LogzioConfiguration {
    private static final Logger logger = LoggerFactory.getLogger(Jmx2LogzioConfiguration.class);

    private Pattern whiteListPattern;
    private Pattern blackListPattern;
    private String jolokiaFullUrl;

//    private Kafka kafka;
    private LogzioJavaSenderParams logzioJavaSenderParams;

    /* Short name of the sampled service, required = false */
    private String serviceName;

    /* host of the sampled service */
    private String serviceHost = null;

    /* Metrics polling interval in seconds */
    private int metricsPollingIntervalInSeconds = 30;

    // Which client should we use
    private MetricClientType metricClientType;

    public enum MetricClientType {
        JOLOKIA,
        MBEAN_PLATFORM
    }

    public Jmx2LogzioConfiguration(Config config) throws IllegalConfiguration {
        if (config.hasPath("service.host")) {
            serviceHost = config.getString("service.host");
        }

        if (config.hasPath("service.poller.jolokia")) {
            metricClientType = MetricClientType.JOLOKIA;
        }
        else if (config.hasPath("service.poller.mbean-direct")) {
            metricClientType = MetricClientType.MBEAN_PLATFORM;
        }

        if (this.metricClientType == MetricClientType.JOLOKIA) {
            jolokiaFullUrl = config.getString("service.poller.jolokia.jolokiaFullUrl");
            String jolokiaHost;
            try {
                URL jolokia = new URL(jolokiaFullUrl);
                jolokiaHost = jolokia.getHost();
            } catch (MalformedURLException e) {
                throw new IllegalConfiguration("service.jolokiaFullUrl must be a valid URL. Error = " + e.getMessage());
            }

            // Setting jolokia url as default
            if (serviceHost == null) {
                serviceHost = jolokiaHost;
            }

        } else if (this.metricClientType == MetricClientType.MBEAN_PLATFORM) {

            // Try to find hostname as default to serviceHost in case it was not provided
            if (serviceHost == null) {
                try {
                    serviceHost = InetAddress.getLocalHost().getHostName();
                } catch (UnknownHostException e) {
                    throw new IllegalConfiguration("service.host was not defined, and could not determine it from the servers hostname");
                }
            }
        }
        try {
            whiteListPattern = Pattern.compile(config.hasPath("service.poller.white-list-regex") ?
                    config.getString("service.poller.white-list-regex") : ".*");
        } catch (Exception e) {
            logger.error("Failed to parse regex {} with error {}",  config.getString("service.poller.white-list-regex"), e.getMessage());
            whiteListPattern = Pattern.compile(".*");
        }

        try { // $a is a regexp that will never match anything (will match an "a" character after the end of the string
            blackListPattern = Pattern.compile(config.hasPath("service.poller.black-list-regex") ?
                    config.getString("service.poller.black-list-regex") : "$a");
        } catch (Exception e) {
            logger.error("Failed to parse regex {} with error {}",  config.getString("service.poller.black-list-regex"), e.getMessage());
            blackListPattern = Pattern.compile("$a");
        }

        serviceName = config.getString("service.name");

        logzioJavaSenderParams = new LogzioJavaSenderParams();
        logzioJavaSenderParams.setUrl(config.hasPath("logzioJavaSender.url") ? config.getString("logzioJavaSender.url") : logzioJavaSenderParams.getUrl());
        logzioJavaSenderParams.setToken(config.getString("logzioJavaSender.token"));

        logzioJavaSenderParams.setFromDisk(config.hasPath("logzioJavaSender.from-disk") ?
                config.getBoolean("logzioJavaSender.from-disk") : logzioJavaSenderParams.isFromDisk());
        logzioJavaSenderParams.setInMemoryQueueCapacityInBytes(config.hasPath("logzioJavaSender.in-memory-queue-capacity") ?
                config.getInt("logzioJavaSender.in-memory-queue-capacity") : logzioJavaSenderParams.getInMemoryQueueCapacityInBytes());
        logzioJavaSenderParams.setLogsCountLimit(config.hasPath("logzioJavaSender.log-count-limit") ?
                config.getInt("logzioJavaSender.log-count-limit") : logzioJavaSenderParams.getLogsCountLimit());

        if (config.hasPath("logzioJavaSender.queue-dir")) {
            File queuePath = new File(config.getString("logzioJavaSender.queue-dir"));
            logzioJavaSenderParams.setQueueDir(queuePath);
        }

        logzioJavaSenderParams.setFileSystemFullPercentThreshold(config.hasPath("logzioJavaSender.file-system-full-percent-threshold") ?
                config.getInt("logzioJavaSender.file-system-full-percent-threshold") : logzioJavaSenderParams.getFileSystemFullPercentThreshold());
        logzioJavaSenderParams.setGcPersistedQueueFilesIntervalSeconds(config.hasPath("logzioJavaSender.clean-sent-metrics-interval") ?
                config.getInt("logzioJavaSender.clean-sent-metrics-interval") : logzioJavaSenderParams.getGcPersistedQueueFilesIntervalSeconds());

if (config.hasPath("metricsPollingIntervalInSeconds")){
            metricsPollingIntervalInSeconds = config.getInt("metricsPollingIntervalInSeconds");
        }
    }

    public String getJolokiaFullUrl() {
        return jolokiaFullUrl;
    }

    public LogzioJavaSenderParams getSenderParams() {
        return this.logzioJavaSenderParams;
    }
//    public String getKafkaUrl() {
//        return kafka.url;
//    }
//
//    public int getQueueCapacity() {
//        return kafka.queueCapacity;
//    }
//
//    public String getKafkaTopic() {
//        return kafka.topic;
//    }

    public String getServiceName() {
        return serviceName;
    }

    public String getServiceHost() {
        return serviceHost;
    }

    public int getMetricsPollingIntervalInSeconds() {
        return metricsPollingIntervalInSeconds;
    }

    public Pattern getWhiteListPattern() {
        return whiteListPattern;
    }

    public Pattern getBlackListPattern() {
        return blackListPattern;
    }

    public MetricClientType getMetricClientType() {
        return metricClientType;
    }

//    public Properties getProducerProperties() {
//        Properties producerProperties = new Properties();
//        producerProperties.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, kafka.url);
//        producerProperties.put(ProducerConfig.ACKS_CONFIG, String.valueOf(kafka.requestRequiredAcks));
//        producerProperties.put(ProducerConfig.BATCH_SIZE_CONFIG, String.valueOf(Math.toIntExact(kafka.batchSizeBytes)));
//        producerProperties.put(ProducerConfig.LINGER_MS_CONFIG, TimeUnit.SECONDS.toMillis(kafka.bulkTimeoutInSeconds));
//        producerProperties.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
//        producerProperties.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
//        producerProperties.put(ProducerConfig.RECONNECT_BACKOFF_MS_CONFIG, kafka.reconnectBackOffMs);
//        producerProperties.put(ProducerConfig.MAX_BLOCK_MS_CONFIG, kafka.maxBlockMs);
//        producerProperties.put(ProducerConfig.RETRY_BACKOFF_MS_CONFIG, kafka.retryBackOffMs);
//        producerProperties.put(ProducerConfig.RETRIES_CONFIG, kafka.numberOfRetries);
//        producerProperties.put(ProducerConfig.CLIENT_ID_CONFIG, kafka.clientId);
//        return producerProperties;
//    }

}
