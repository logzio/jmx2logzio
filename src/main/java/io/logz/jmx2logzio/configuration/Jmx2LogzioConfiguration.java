package io.logz.jmx2logzio.configuration;

import com.google.common.base.Splitter;
import com.typesafe.config.Config;
import io.logz.jmx2logzio.Jmx2LogzioJolokia;
import io.logz.jmx2logzio.clients.JolokiaClient;
import io.logz.jmx2logzio.exceptions.IllegalConfiguration;
import io.logz.jmx2logzio.objects.Dimension;
import io.logz.jmx2logzio.objects.LogzioJavaSenderParams;
import org.apache.commons.validator.routines.UrlValidator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.InetAddress;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Objects;
import java.util.regex.Pattern;
import java.util.stream.Collectors;


public class Jmx2LogzioConfiguration {
    private static final String JMX2LOGZIO_AGENT_VERSION_DIMENSION = "jmx2logzio.agent.version";
    private static final int KEY_INDEX = 0;
    private static final int VALUE_INDEX = 1;
    private final Logger logger = LoggerFactory.getLogger(Jmx2LogzioConfiguration.class);

    private static final String POLLER_MBEAN_DIRECT = "service.poller.mbean-direct";

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

    private MetricEndpointType metricEndpointType;

    private List<Dimension> extraDimensions;

    public enum MetricClientType {
        JOLOKIA,
        MBEAN_PLATFORM
    }

    public enum MetricEndpointType {
        JSON_HTTP,
        PROMETHEUS_REMOTE_WRITE
    }

    public Jmx2LogzioConfiguration(Config config) throws IllegalConfiguration {
        if (config.hasPath(Jmx2LogzioJolokia.SERVICE_HOST)) {
            serviceHost = config.getString(Jmx2LogzioJolokia.SERVICE_HOST);
        }
        setClient(config);
        setFilterPatterns(config);
        serviceName = config.getString(Jmx2LogzioJolokia.SERVICE_NAME);
        logzioJavaSenderParams = new LogzioJavaSenderParams();
        setListenerURL(config);
        metricEndpointType = config.getString(Jmx2LogzioJolokia.ENDPOINT_TYPE) != null ? config.getEnum(MetricEndpointType.class, Jmx2LogzioJolokia.ENDPOINT_TYPE) : MetricEndpointType.JSON_HTTP;

        extraDimensions = new ArrayList<>();
        if (config.hasPath(Jmx2LogzioJolokia.EXTRA_DIMENSIONS)) {
            if (metricClientType == MetricClientType.MBEAN_PLATFORM) {
                extraDimensions = parseExtraDimensions(config.getString(Jmx2LogzioJolokia.EXTRA_DIMENSIONS));
            } else {
                extraDimensions = parseExtraDimensions(config.getConfig(Jmx2LogzioJolokia.EXTRA_DIMENSIONS));
            }
        }
        final Properties properties = new Properties();
        try {
            properties.load(this.getClass().getClassLoader().getResourceAsStream(".properties"));
            extraDimensions.add(new Dimension(JMX2LOGZIO_AGENT_VERSION_DIMENSION, properties.getProperty("agent.version")));
        } catch (IOException | NullPointerException e) {
            logger.warn("couldn't add jmx2logzio agent version as a dimension", e);
        }

        if (config.getString(Jmx2LogzioJolokia.LOGZIO_TOKEN).equals("<ACCOUNT-TOKEN>")) {
            throw new IllegalConfiguration("please enter a valid logz.io token (can be located at https://app.logz.io/#/dashboard/settings/manage-accounts)");
        }
        logzioJavaSenderParams.setToken(config.getString(Jmx2LogzioJolokia.LOGZIO_TOKEN));

        ConfigSetter configSetter = (fromDisk) -> logzioJavaSenderParams.setFromDisk((boolean) fromDisk);
        setSingleConfig(config, Jmx2LogzioJolokia.FROM_DISK, null, configSetter, new ConfigValidator() {
        }, Boolean.class);


        if (logzioJavaSenderParams.isFromDisk()) {
            setDiskStorageParams(config);
        } else {
            setInMemoryParams(config);
        }

        configSetter = (interval) -> metricsPollingIntervalInSeconds = (int) interval;
        validateAndSetNatural(config, Jmx2LogzioJolokia.METRICS_POLLING_INTERVAL, metricsPollingIntervalInSeconds, configSetter);

    }

    private List<Dimension> parseExtraDimensions(Config config) {
        List<Dimension> result = new ArrayList<>();
        config.entrySet().forEach(entry ->
                result.add(new Dimension(entry.getKey(), config.getString(entry.getKey()))));
        return result;
    }


    private List<Dimension> parseExtraDimensions(String extraParams) {
        if (extraParams.charAt(0) != '{' || extraParams.charAt(extraParams.length() - 1) != '}') {
            logger.error("malformed missing encapsulating chars '{' or '}' or wrong extra dimensions pattern - expected pattern is {key=value:key=value...} , ignoring extra dimensions..");
            return new ArrayList<>();
        }
        extraParams = extraParams.substring(1, extraParams.length() - 1);
        return Splitter.on(':').splitToList(extraParams).stream().map((param) -> {
            try {
                String[] keyval = param.split("=");
                if (keyval[KEY_INDEX].isEmpty() || keyval[VALUE_INDEX].isEmpty()) {
                    throw new IllegalConfiguration("Dimension's key and/or value can't be empty");
                }
                return new Dimension(keyval[KEY_INDEX], keyval[VALUE_INDEX]);
            } catch (IndexOutOfBoundsException | IllegalConfiguration e) {
                logger.error(String.format("malformed extra dimensions pattern - expected pattern is {key=value:key=value...} , ignoring extra dimension: %s", param), e);
            }
            return null;
        })
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }

    private void setDiskStorageParams(Config config) {
        ConfigSetter configSetter = (interval) -> logzioJavaSenderParams.setDiskSpaceCheckInterval((int) interval);
        validateAndSetNatural(config, Jmx2LogzioJolokia.DISK_SPACE_CHECK_INTERVAL, logzioJavaSenderParams.getDiskSpaceCheckInterval(), configSetter);

        configSetter = (limit) -> logzioJavaSenderParams.setFileSystemFullPercentThreshold((int) limit);
        validateAndSetNatural(config, Jmx2LogzioJolokia.FILE_SYSTEM_SPACE_LIMIT, logzioJavaSenderParams.getFileSystemFullPercentThreshold(), configSetter);

        configSetter = (interval) -> logzioJavaSenderParams.setGcPersistedQueueFilesIntervalSeconds((int) interval);
        validateAndSetNatural(config, Jmx2LogzioJolokia.CLEAN_SENT_METRICS_INTERVAL, logzioJavaSenderParams.getGcPersistedQueueFilesIntervalSeconds(), configSetter);
    }


    private void setInMemoryParams(Config config) {
        ConfigSetter configSetter = (capacity) -> logzioJavaSenderParams.setInMemoryQueueCapacityInBytes((int) capacity);
        validateAndSetNatural(config, Jmx2LogzioJolokia.IN_MEMORY_QUEUE_CAPACITY, logzioJavaSenderParams.getInMemoryQueueCapacityInBytes(), configSetter);

        configSetter = (limit) -> logzioJavaSenderParams.setLogsCountLimit((int) limit);
        validateAndSetNatural(config, Jmx2LogzioJolokia.LOGS_COUNT_LIMIT, logzioJavaSenderParams.getInMemoryQueueCapacityInBytes(), configSetter);
    }

    private void setListenerURL(Config config) {
        ConfigSetter configSetter = (url) -> logzioJavaSenderParams.setUrl((String) url);
        setSingleConfig(config, Jmx2LogzioJolokia.LISTENER_URL, null, configSetter, new ConfigValidator() {}, String.class);
    }

    private void setFilterPatterns(Config config) {
        try {
            whiteListPattern = Pattern.compile(config.hasPath(Jmx2LogzioJolokia.WHITE_LIST_REGEX) ?
                    config.getString(Jmx2LogzioJolokia.WHITE_LIST_REGEX) : ".*");
        } catch (Exception e) {
            logger.error("Failed to parse regex {} with error {}", config.getString(Jmx2LogzioJolokia.WHITE_LIST_REGEX), e.getMessage(), e);
            whiteListPattern = Pattern.compile(".*");
        }

        try {
            blackListPattern = Pattern.compile(config.hasPath(Jmx2LogzioJolokia.BLACK_LIST_REGEX) ?
                    config.getString(Jmx2LogzioJolokia.BLACK_LIST_REGEX) : "$a"); // $a is a regexp that will never match anything (will match an "a" character after the end of the string
        } catch (Exception e) {
            logger.error("Failed to parse regex {} with error {}", config.getString(Jmx2LogzioJolokia.WHITE_LIST_REGEX), e.getMessage(), e);
            blackListPattern = Pattern.compile("$a");
        }
    }

    private void setClient(Config config) {

        if (config.hasPath(JolokiaClient.POLLER_JOLOKIA)) {
            metricClientType = MetricClientType.JOLOKIA;
            if (!config.hasPath(JolokiaClient.JOLOKIA_FULL_URL)) {
                throw new IllegalConfiguration("service.poller.jolokiaFullUrl has to be in the configuration file (application.conf)");
            }
            jolokiaFullUrl = config.getString(JolokiaClient.JOLOKIA_FULL_URL);
            String jolokiaHost;
            try {
                URL jolokia = new URL(jolokiaFullUrl);
                jolokiaHost = jolokia.getHost();
            } catch (MalformedURLException e) {
                throw new IllegalConfiguration("service.poller.jolokiaFullUrl must be a valid URL. Error = " + e.getMessage());
            }

            // Setting jolokia url as default
            if (serviceHost == null) {
                serviceHost = jolokiaHost;
            }
        } else if (config.hasPath(POLLER_MBEAN_DIRECT)) {
            metricClientType = MetricClientType.MBEAN_PLATFORM;
            // Try to find hostname as default to serviceHost in case it was not provided
            if (serviceHost == null) {
                try {
                    serviceHost = InetAddress.getLocalHost().getHostName();
                } catch (UnknownHostException e) {
                    throw new IllegalConfiguration("service.host was not defined, and could not determine it from the servers hostname");
                }
            }
        } else {
            throw new IllegalConfiguration("Client TYPE has to be either Jolokia or MBean");
        }
    }

    private void setSingleConfig(Config config, String paramString, String errMsg, ConfigSetter setter, ConfigValidator validator, Class<?> configClass) {
        if (config.hasPath(paramString)) {
            Map<Class<?>, ConfigGetter> getValueByType = new HashMap<>();
            getValueByType.put(String.class, config::getString);
            getValueByType.put(Integer.class, config::getInt);
            getValueByType.put(Boolean.class, config::getBoolean);
            Object value = getValueByType.get(configClass).getConfig(paramString);

            if (validator.validatePredicate(value)) {
                setter.setOperation(value);
            } else {
                logger.error(errMsg, value);
            }
        }
    }

    private void validateAndSetNatural(Config config, String arg, int defaultValue, ConfigSetter setter) {
        ConfigValidator validator = new ConfigValidator() {
            @Override
            public boolean validatePredicate(Object result) {
                return (int) result > 0;
            }
        };
        setSingleConfig(config, arg, "argument " + arg + " has to be a natural number, using default instead: " + defaultValue, setter, validator, Integer.class);
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

    public MetricEndpointType getMetricEndpointType() { return metricEndpointType; }

    public List<Dimension> getExtraDimensions() {
        return extraDimensions;
    }

}
