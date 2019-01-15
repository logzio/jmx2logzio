package io.logz.jmx2logzio.configuration;

import com.typesafe.config.Config;
import io.logz.jmx2logzio.Jmx2LogzioJavaAgent;
import io.logz.jmx2logzio.exceptions.IllegalConfiguration;
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

    public static final String LISTENER_URL = "LISTENER_URL";
    public static final String WHITE_LIST_REGEX = "WHITE_LIST_REGEX";
    public static final String BLACK_LIST_REGEX = "BLACK_LIST_REGEX";
    public static final String LOGZIO_TOKEN = "LOGZIO_TOKEN";
    public static final String SERVICE_NAME = "SERVICE_NAME";
    public static final String SERVICE_HOST = "SERVICE_HOST";
    public static final String POLLING_INTERVAL_IN_SEC = "POLLING_INTERVAL_IN_SEC";
    public static final String FROM_DISK = "FROM_DISK";
    public static final String IN_MEMORY_QUEUE_CAPACITY = "IN_MEMORY_QUEUE_CAPACITY";
    public static final String LOGS_COUNT_LIMIT = "LOGS_COUNT_LIMIT";
    public static final String DISK_SPACE_CHECKS_INTERVAL = "DISK_SPACE_CHECKS_INTERVAL";
    public static final String QUEUE_DIR = "QUEUE_DIR";
    public static final String FILE_SYSTEM_SPACE_LIMIT = "FILE_SYSTEM_SPACE_LIMIT";
    public static final String CLEAN_SENT_METRICS_INTERVAL = "CLEAN_SENT_METRICS_INTERVAL";

    private Pattern whiteListPattern;
    private Pattern blackListPattern;
    private String jolokiaFullUrl;

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
        if (config.hasPath(Jmx2LogzioJavaAgent.SERVICE_HOST)) {
            serviceHost = config.getString(Jmx2LogzioJavaAgent.SERVICE_HOST);
        }

        if (config.hasPath(Jmx2LogzioJavaAgent.POLLER_JOLOKIA)) {
            metricClientType = MetricClientType.JOLOKIA;
        } else if (config.hasPath(Jmx2LogzioJavaAgent.POLLER_MBEAN_DIRECT)) {
            metricClientType = MetricClientType.MBEAN_PLATFORM;
        }

        if (this.metricClientType == MetricClientType.JOLOKIA) {
            jolokiaFullUrl = config.getString(Jmx2LogzioJavaAgent.JOLOKIA_FULL_URL);
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
            whiteListPattern = Pattern.compile(config.hasPath(Jmx2LogzioJavaAgent.WHITE_LIST_REGEX) ?
                    config.getString(Jmx2LogzioJavaAgent.WHITE_LIST_REGEX) : ".*");
        } catch (Exception e) {
            logger.error("Failed to parse regex {} with error {}", config.getString(Jmx2LogzioJavaAgent.WHITE_LIST_REGEX), e.getMessage());
            whiteListPattern = Pattern.compile(".*");
        }

        try { // $a is a regexp that will never match anything (will match an "a" character after the end of the string
            blackListPattern = Pattern.compile(config.hasPath(Jmx2LogzioJavaAgent.BLACK_LIST_REGEX) ?
                    config.getString(Jmx2LogzioJavaAgent.BLACK_LIST_REGEX) : "$a");
        } catch (Exception e) {
            logger.error("Failed to parse regex {} with error {}", config.getString(Jmx2LogzioJavaAgent.WHITE_LIST_REGEX), e.getMessage());
            blackListPattern = Pattern.compile("$a");
        }

        serviceName = config.getString(Jmx2LogzioJavaAgent.SERVICE_NAME);

        logzioJavaSenderParams = new LogzioJavaSenderParams();
        if (config.hasPath(LogzioJavaSenderParams.LISTENER_URL)) {
            URL url = null;
            try {
                url = new URL(config.getString(LogzioJavaSenderParams.LISTENER_URL));
                logzioJavaSenderParams.setUrl(url.toString());
            } catch (MalformedURLException e) {
                logger.error("malformed listener URL {} got error {}. Using default listener URL: {}",url.toString(),e.getMessage(),logzioJavaSenderParams.getUrl());
            }
        }
        logzioJavaSenderParams.setToken(config.getString(LogzioJavaSenderParams.LOGZIO_TOKEN));

        logzioJavaSenderParams.setFromDisk(config.hasPath(LogzioJavaSenderParams.FROM_DISK) ?
                config.getBoolean(LogzioJavaSenderParams.FROM_DISK) : logzioJavaSenderParams.isFromDisk());

        if (config.hasPath(LogzioJavaSenderParams.IN_MEMORY_QUEUE_CAPACITY)) {
            int capacity = config.getInt(LogzioJavaSenderParams.IN_MEMORY_QUEUE_CAPACITY);
            if (capacity > 0) {
                logzioJavaSenderParams.setInMemoryQueueCapacityInBytes(capacity);
            } else {
                logger.error("argument IN_MEMORY_QUEUE_CAPACITY has to be a natural number, using default instead: {}",logzioJavaSenderParams.getInMemoryQueueCapacityInBytes());
            }
        }

        if (config.hasPath(LogzioJavaSenderParams.LOGS_COUNT_LIMIT)) {
            int logCountLimit = config.getInt(LogzioJavaSenderParams.LOGS_COUNT_LIMIT);
            if (logCountLimit > 0) {
                logzioJavaSenderParams.setLogsCountLimit(logCountLimit);
            } else {
                logger.error("argument LOGS_COUNT_LIMIT has to be a natural number, using default instead: {}", logzioJavaSenderParams.getLogsCountLimit());
            }
        }

        if (config.hasPath(LogzioJavaSenderParams.DISK_SPACE_CHECK_INTERVAL)) {
            int interval = config.getInt(LogzioJavaSenderParams.DISK_SPACE_CHECK_INTERVAL);
            if (interval > 0) {
                logzioJavaSenderParams.setDiskSpaceCheckInterval(interval);
            } else {
                logger.error("argument DISK_SPACE_CHECKS_INTERVAL has to be a natural number, using default instead: {}", logzioJavaSenderParams.getDiskSpaceCheckInterval());
            }
        }

        if (config.hasPath(LogzioJavaSenderParams.QUEUE_DIR)) {
            File queuePath = new File(config.getString(LogzioJavaSenderParams.QUEUE_DIR));
            logzioJavaSenderParams.setQueueDir(queuePath);
        }

        if (config.hasPath(LogzioJavaSenderParams.FILE_SYSTEM_SPACE_LIMIT)) {
            int spaceLimit = config.getInt(LogzioJavaSenderParams.FILE_SYSTEM_SPACE_LIMIT);
            if (spaceLimit > 0) {
                logzioJavaSenderParams.setFileSystemFullPercentThreshold(spaceLimit);
            } else {
                logger.error("argument FILE_SYSTEM_SPACE_LIMIT has to be a natural number, using default instead: {}", logzioJavaSenderParams.getFileSystemFullPercentThreshold());
            }
        }

        if (config.hasPath(LogzioJavaSenderParams.CLEAN_SENT_METRICS_INTERVAL)) {
            int interval = config.getInt(LogzioJavaSenderParams.CLEAN_SENT_METRICS_INTERVAL);
            if (interval > 0) {
                logzioJavaSenderParams.setGcPersistedQueueFilesIntervalSeconds(interval);
            } else {
                logger.error("argument CLEAN_SENT_METRICS_INTERVAL has to be a natural number, using default instead: {}", logzioJavaSenderParams.getGcPersistedQueueFilesIntervalSeconds());
            }
        }

        if (config.hasPath(Jmx2LogzioJavaAgent.METRICS_POLLING_INTERVAL)) {
            int interval = config.getInt(Jmx2LogzioJavaAgent.METRICS_POLLING_INTERVAL);
            if (interval > 0) {
                metricsPollingIntervalInSeconds = interval;
            } else {
                logger.error("argument POLLING_INTERVAL_IN_SEC has to be a natural number, using default instead: {}", metricsPollingIntervalInSeconds);
            }
        }
    }

    public String getJolokiaFullUrl() {
        return jolokiaFullUrl;
    }

    public LogzioJavaSenderParams getSenderParams() {
        return this.logzioJavaSenderParams;
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

}
