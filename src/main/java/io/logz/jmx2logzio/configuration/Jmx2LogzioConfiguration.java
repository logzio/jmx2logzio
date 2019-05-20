package io.logz.jmx2logzio.configuration;

import org.slf4j.Logger;
import com.google.common.base.Splitter;
import com.typesafe.config.Config;
import io.logz.jmx2logzio.Jmx2LogzioJolokia;
import io.logz.jmx2logzio.clients.JolokiaClient;
import io.logz.jmx2logzio.exceptions.IllegalConfiguration;
import io.logz.jmx2logzio.objects.Dimension;
import io.logz.jmx2logzio.objects.LogzioJavaSenderParams;
import org.apache.commons.validator.routines.UrlValidator;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.net.InetAddress;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.stream.Collectors;


public class Jmx2LogzioConfiguration {
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
    private List<Dimension> extraDimensions;

    public enum MetricClientType {
        JOLOKIA,
        MBEAN_PLATFORM
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

        extraDimensions = new ArrayList<>();
        if (config.hasPath(Jmx2LogzioJolokia.EXTRA_DIMENSIONS)) {
            if (metricClientType == MetricClientType.MBEAN_PLATFORM) {
                extraDimensions = parseExtraDimensions(config.getString(Jmx2LogzioJolokia.EXTRA_DIMENSIONS));
            } else {
                extraDimensions = parseExtraDimensions(config.getConfig(Jmx2LogzioJolokia.EXTRA_DIMENSIONS));
            }
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
                result.add(new Dimension(entry.getKey(),config.getString(entry.getKey()))));
        return result;
    }


    private List<Dimension> parseExtraDimensions(String extraParams) {
        if (extraParams.charAt(0) != '{' || extraParams.charAt(extraParams.length()-1) != '}') {
            logger.error("malformed missing encapsulating chars '{' or '}' or wrong extra dimensions pattern - expected pattern is {key=value:key=value...} , ignoring extra dimensions..");
            return new ArrayList<>();
        }
        extraParams = extraParams.substring(1,extraParams.length()-1);
        List<Dimension> result = Splitter.on(':').splitToList(extraParams).stream().map((param) -> {
            String[] keyval = param.split("=");
            return new Dimension(keyval[0],keyval[1]);
        }).collect(Collectors.toList());
        return result;
    }

    private void setDiskStorageParams(Config config) {
        ConfigSetter configSetter = (interval) -> logzioJavaSenderParams.setDiskSpaceCheckInterval((int) interval);
        validateAndSetNatural(config, Jmx2LogzioJolokia.DISK_SPACE_CHECK_INTERVAL, logzioJavaSenderParams.getDiskSpaceCheckInterval(), configSetter);

        configSetter = (queuePath) -> logzioJavaSenderParams.setQueueDir(new File((String) queuePath));
        setSingleConfig(config, Jmx2LogzioJolokia.QUEUE_DIR, null, configSetter, new ConfigValidator() {
        }, String.class);

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

        ConfigValidator urlValidator = new ConfigValidator() {
            @Override
            public boolean validatePredicate(Object result) {
                String urlString = (String) result;
                urlString = urlString.substring(0,(urlString.lastIndexOf(":")));
                return UrlValidator.getInstance().isValid(urlString);
            }
        };
        String malformedURLMsg = "URL {} is invalid. Using default listener URL: " + logzioJavaSenderParams.getUrl();
        ConfigSetter configSetter = (url) -> logzioJavaSenderParams.setUrl((String) url);
        setSingleConfig(config, Jmx2LogzioJolokia.LISTENER_URL, malformedURLMsg, configSetter, urlValidator, String.class);
    }

    private void setFilterPatterns(Config config) {
        try {
            whiteListPattern = Pattern.compile(config.hasPath(Jmx2LogzioJolokia.WHITE_LIST_REGEX) ?
                    config.getString(Jmx2LogzioJolokia.WHITE_LIST_REGEX) : ".*");
        } catch (Exception e) {
            logger.error("Failed to parse regex {} with error {}", config.getString(Jmx2LogzioJolokia.WHITE_LIST_REGEX), e.getMessage());
            whiteListPattern = Pattern.compile(".*");
        }

        try {
            blackListPattern = Pattern.compile(config.hasPath(Jmx2LogzioJolokia.BLACK_LIST_REGEX) ?
                    config.getString(Jmx2LogzioJolokia.BLACK_LIST_REGEX) : "$a"); // $a is a regexp that will never match anything (will match an "a" character after the end of the string
        } catch (Exception e) {
            logger.error("Failed to parse regex {} with error {}", config.getString(Jmx2LogzioJolokia.WHITE_LIST_REGEX), e.getMessage());
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
            throw new IllegalConfiguration("Client type has to be either Jolokia or MBean");
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

    public List<Dimension> getExtraDimensions() {
        return extraDimensions;
    }

}
