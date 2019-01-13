package io.logz.jmx2logzio;

import com.google.common.base.Splitter;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import io.logz.jmx2logzio.configuration.Jmx2LogzioConfiguration;
import io.logz.jmx2logzio.exceptions.IllegalConfiguration;
import io.logz.jmx2logzio.objects.LogzioJavaSenderParams;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.instrument.Instrumentation;
import java.util.HashMap;
import java.util.Map;

/**
 * Created by roiravhon on 6/6/16.
 */
public class Jmx2LogzioJavaAgent {

    public static final String POLLER_JOLOKIA = "service.poller.jolokia";
    public static final String POLLER_MBEAN_DIRECT = "service.poller.mbean-direct";
    public static final String JOLOKIA_FULL_URL = "service.poller.jolokia.jolokiaFullUrl";
    public static String WHITE_LIST_REGEX = "service.poller.white-list-regex";
    public static String BLACK_LIST_REGEX = "service.poller.BLACK-list-regex";
    public static String SERVICE_NAME = "service.name";
    public static String SERVICE_HOST = "service.host";
    public static String METRICS_POLLING_INTERVAL = "metricsPollingIntervalInSeconds";

    private static final Logger logger = LoggerFactory.getLogger(Jmx2LogzioJavaAgent.class);

    public static void premain(String agentArgument, Instrumentation inst) {

        logger.info("Loading with agentArgument: {}", agentArgument);

        Config finalConfig = getIntegratedConfiguration(agentArgument);
        Jmx2LogzioConfiguration jmx2LogzioConfiguration = new Jmx2LogzioConfiguration(finalConfig);

        Jmx2Logzio main = new Jmx2Logzio(jmx2LogzioConfiguration);
        logger.info("Initiated new java agent based Jmx2Logzio instance");

        try {
            main.run();

            // Catching anything, because if we throw exception here, it will stop the main thread as well.
        } catch (Throwable e) {
            logger.error("Stopping jmx2logzio Java Agent due to unexpected exception: " + e.getMessage(), e);
        }
    }

    private static Config getIntegratedConfiguration(String agentArgument) {
        Map<String, String> configurationMap = parseArgumentsString(agentArgument);

        if (configurationMap.get(getArgumentConfigurationRepresentation(Jmx2LogzioConfiguration.SERVICE_NAME)) == null) {
            throw new IllegalConfiguration("SERVICE_NAME must be one of the arguments");
        }
        if (configurationMap.get(getArgumentConfigurationRepresentation(Jmx2LogzioConfiguration.LOGZIO_TOKEN)) == null) {
            throw new IllegalConfiguration("LOGZIO_TOKEN must be one of the arguments");
        }

        Config userConfig = ConfigFactory.parseMap(configurationMap);
        Config fileConfig = ConfigFactory.load("javaagent.conf");

        // Merge the two configurations
        return userConfig.withFallback(fileConfig);
    }

    private static Map<String, String> parseArgumentsString(String arguments) throws IllegalConfiguration {
        try {
            Map<String, String> argumentsMap = new HashMap<>();
            Map<String, String> keyValues = Splitter.on(';').omitEmptyStrings().withKeyValueSeparator('=').split(arguments);

            keyValues.forEach((k, v) ->
                    argumentsMap.put(getArgumentConfigurationRepresentation(k), v));

            return argumentsMap;

        } catch (IllegalArgumentException e) {
            throw new IllegalConfiguration("Java agent arguments must be in form of: key=value;key=value");
        }
    }

    private static String getArgumentConfigurationRepresentation(String key) throws IllegalConfiguration {

        switch (key) {
            case Jmx2LogzioConfiguration.LISTENER_URL:
                return LogzioJavaSenderParams.LISTENER_URL;
            case Jmx2LogzioConfiguration.WHITE_LIST_REGEX:
                return Jmx2LogzioJavaAgent.WHITE_LIST_REGEX;
            case Jmx2LogzioConfiguration.BLACK_LIST_REGEX:
                return Jmx2LogzioJavaAgent.BLACK_LIST_REGEX;
            case Jmx2LogzioConfiguration.LOGZIO_TOKEN:
                return LogzioJavaSenderParams.LOGZIO_TOKEN;
            case Jmx2LogzioConfiguration.SERVICE_NAME:
                return Jmx2LogzioJavaAgent.SERVICE_NAME;
            case Jmx2LogzioConfiguration.SERVICE_HOST:
                return Jmx2LogzioJavaAgent.SERVICE_HOST;
            case Jmx2LogzioConfiguration.INTERVAL_IN_SEC:
                return Jmx2LogzioJavaAgent.METRICS_POLLING_INTERVAL;
            case Jmx2LogzioConfiguration.FROM_DISK:
                return  LogzioJavaSenderParams.FROM_DISK;
            case Jmx2LogzioConfiguration.IN_MEMORY_QUEUE_CAPACITY:
                return LogzioJavaSenderParams.IN_MEMORY_QUEUE_CAPACITY;
            case Jmx2LogzioConfiguration.LOGS_COUNT_LIMIT:
                return LogzioJavaSenderParams.LOGS_COUNT_LIMIT;
            case Jmx2LogzioConfiguration.DISK_SPACE_CHECKS_INTERVAL:
                return LogzioJavaSenderParams.DISK_SPACE_CHECK_INTERVAL;
            case Jmx2LogzioConfiguration.QUEUE_DIR:
                return LogzioJavaSenderParams.QUEUE_DIR;
            case Jmx2LogzioConfiguration.FILE_SYSTEM_SPACE_LIMIT:
                return LogzioJavaSenderParams.FILE_SYSTEM_SPACE_LIMIT;
            case Jmx2LogzioConfiguration.CLEAN_SENT_METRICS_INTERVAL:
                return LogzioJavaSenderParams.CLEAN_SENT_METRICS_INTERVAL;
            default:
                throw new IllegalConfiguration("Unknown configuration option: " + key);
        }
    }
}
