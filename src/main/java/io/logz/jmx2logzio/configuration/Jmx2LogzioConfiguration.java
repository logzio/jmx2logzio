package io.logz.jmx2logzio.configuration;

import com.typesafe.config.Config;
import io.logz.jmx2logzio.exceptions.IllegalConfiguration;
//import org.apache.kafka.clients.producer.ProducerConfig;
//import org.apache.kafka.common.serialization.StringSerializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetAddress;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.UnknownHostException;
import java.util.Properties;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

public class Jmx2LogzioConfiguration {
    private static final Logger logger = LoggerFactory.getLogger(Jmx2LogzioConfiguration.class);

    private Pattern whiteListPattern;
    private Pattern blackListPattern;
    private String jolokiaFullUrl;

    private Kafka kafka;

    /* Short name of the sampled service, required = false */
    private String serviceName;

    /* host of the sampled service */
    private String serviceHost = null;

    /* Metrics polling interval in seconds */
    private int metricsPollingIntervalInSeconds;

    // Which client should we use
    private MetricClientType metricClientType;

    public enum MetricClientType {
        JOLOKIA,
        MBEAN_PLATFORM
    }

    private class Kafka {
        String url;
        String topic;
        String clientId = "jmx-metrics-producer";
        long batchSizeBytes = 10240;
        int bulkTimeoutInSeconds = 1;
        int requestRequiredAcks = 1;
        int reconnectBackOffMs = 10000;
        int maxBlockMs = 10000;
        int retryBackOffMs = 1000;
        int numberOfRetries = 0;
        int queueCapacity = 100000;
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
            whiteListPattern = Pattern.compile(config.hasPath("service.poller.white-list-regex") ? config.getString("service.poller.white-list-regex") : ".*");
        } catch (Exception e) {
            logger.error("Failed to parse regex {} with error {}",  config.getString("service.poller.white-list-regex"), e.getMessage());
            whiteListPattern = Pattern.compile(".*");
        }

        try {
            blackListPattern = Pattern.compile(config.hasPath("service.poller.black-list-regex") ? config.getString("service.poller.black-list-regex") : ".*");
        } catch (Exception e) {
            logger.error("Failed to parse regex {} with error {}",  config.getString("service.poller.black-list-regex"), e.getMessage());
            blackListPattern = Pattern.compile(".*");
        }


        kafka = new Kafka();
        kafka.url = config.getString("kafka.url");
        kafka.topic = config.getString("kafka.topic");
        kafka.clientId = config.hasPath("kafka.clientId") ? config.getString("kafka.clientId") : kafka.clientId;
        kafka.batchSizeBytes = config.hasPath("kafka.batchSizeBytes") ? config.getLong("kafka.batchSizeBytes") : kafka.batchSizeBytes;
        kafka.requestRequiredAcks = config.hasPath("kafka.requestRequiredAcks") ? config.getInt("kafka.requestRequiredAcks") : kafka.requestRequiredAcks;
        kafka.bulkTimeoutInSeconds = config.hasPath("kafka.bulkTimeoutInSeconds") ? config.getInt("kafka.bulkTimeoutInSeconds") : kafka.bulkTimeoutInSeconds;
        kafka.reconnectBackOffMs = config.hasPath("kafka.reconnectBackOffMs") ? config.getInt("kafka.reconnectBackOffMs") : kafka.reconnectBackOffMs;
        kafka.maxBlockMs = config.hasPath("kafka.maxBlockMs") ? config.getInt("kafka.maxBlockMs") : kafka.maxBlockMs;
        kafka.numberOfRetries = config.hasPath("kafka.numberOfRetries") ? config.getInt("kafka.numberOfRetries") : kafka.numberOfRetries;
        kafka.retryBackOffMs = config.hasPath("kafka.retryBackOffMs") ? config.getInt("kafka.retryBackOffMs") : kafka.retryBackOffMs;
        kafka.queueCapacity = config.hasPath("kafka.queueCapacity") ? config.getInt("kafka.queueCapacity") : kafka.queueCapacity;
        metricsPollingIntervalInSeconds = config.getInt("metricsPollingIntervalInSeconds");

        serviceName = config.getString("service.name");
    }

    public String getJolokiaFullUrl() {
        return jolokiaFullUrl;
    }

    public String getKafkaUrl() {
        return kafka.url;
    }

    public int getQueueCapacity() {
        return kafka.queueCapacity;
    }

    public String getKafkaTopic() {
        return kafka.topic;
    }

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
