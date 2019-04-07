package io.logz.jmx2logzio;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import io.logz.jmx2logzio.clients.JavaAgentClient;
import io.logz.jmx2logzio.clients.JolokiaClient;
import io.logz.jmx2logzio.configuration.Jmx2LogzioConfiguration;
import io.logz.jmx2logzio.exceptions.IllegalConfiguration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.instrument.Instrumentation;
import java.util.HashMap;
import java.util.Map;

public class Jmx2LogzioJavaAgent {

    public static final String WHITE_LIST_REGEX = "service.poller.white-list-regex";
    public static final String BLACK_LIST_REGEX = "service.poller.black-list-regex";
    public static final String SERVICE_NAME = "service.name";
    public static final String SERVICE_HOST = "service.host";
    public static final String METRICS_POLLING_INTERVAL = "service.poller.metrics-polling-interval-in-seconds";
    public static final String EXTRA_DIMENSIONS = "extra-dimensions";

    private static final Logger logger = LoggerFactory.getLogger(Jmx2LogzioJavaAgent.class);
    private static final String JAVA_AGENT_CONFIGURATION_FILE = "javaagent.conf";
    private static final int SPLIT_KEY_VALUE_COUNT_LIMIT = 2;
    private static final int INDEX_OF_KEY = 0;
    private static final int INDEX_OF_VALUE = 1;

    public static void premain(String agentArgument, Instrumentation instrument) {

        logger.info("Loading with agentArgument: {}", agentArgument);
        Config finalConfig = getIntegratedConfiguration(agentArgument);
        Jmx2LogzioConfiguration jmx2LogzioConfiguration = new Jmx2LogzioConfiguration(finalConfig);

        Jmx2Logzio main = new Jmx2Logzio(jmx2LogzioConfiguration);
        logger.info("Initiated new java agent based Jmx2Logzio instance");

        try {
            main.run();
        } catch (Throwable e) {
            logger.error("Stopping jmx2logzio Java Agent due to unexpected exception: " + e.getMessage(), e);
        }
    }

    private static Config getIntegratedConfiguration(String agentArgument) {
        Map<String, String> configurationMap = parseArgumentsString(agentArgument);

        if (configurationMap.get(getArgumentConfigurationRepresentation(JolokiaClient.SERVICE_NAME)) == null) {
            throw new IllegalConfiguration("SERVICE_NAME must be one of the arguments");
        }
        if (configurationMap.get(getArgumentConfigurationRepresentation(JolokiaClient.LOGZIO_TOKEN)) == null) {
            throw new IllegalConfiguration("LOGZIO_TOKEN must be one of the arguments");
        }

        Config userConfig = ConfigFactory.parseMap(configurationMap);
        Config fileConfig = ConfigFactory.load(JAVA_AGENT_CONFIGURATION_FILE);

        // Merge the two configurations
        return userConfig.withFallback(fileConfig);
    }

    public static Map<String, String> parseArgumentsString(String arguments) throws IllegalConfiguration {
        try {
            Map<String, String> argumentsMap = new HashMap<>();
            for (String argument : arguments.split(",")) {
                String[] keyval = argument.split("=", SPLIT_KEY_VALUE_COUNT_LIMIT);
                argumentsMap.put(Jmx2LogzioJavaAgent.getArgumentConfigurationRepresentation(keyval[INDEX_OF_KEY]),keyval[INDEX_OF_VALUE]);
            }

            return argumentsMap;

        } catch (IllegalArgumentException e) {
            throw new IllegalConfiguration("Java agent arguments must be in form of: key=value;key=value");
        }
    }

    public static String getArgumentConfigurationRepresentation(String key) throws IllegalConfiguration {

        switch (key) {
            case JolokiaClient.LISTENER_URL:
                return JavaAgentClient.LISTENER_URL;
            case JolokiaClient.WHITE_LIST_REGEX:
                return Jmx2LogzioJavaAgent.WHITE_LIST_REGEX;
            case JolokiaClient.BLACK_LIST_REGEX:
                return Jmx2LogzioJavaAgent.BLACK_LIST_REGEX;
            case JolokiaClient.EXTRA_DIMENSIONS:
                return Jmx2LogzioJavaAgent.EXTRA_DIMENSIONS;
            case JolokiaClient.LOGZIO_TOKEN:
                return JavaAgentClient.LOGZIO_TOKEN;
            case JolokiaClient.SERVICE_NAME:
                return Jmx2LogzioJavaAgent.SERVICE_NAME;
            case JolokiaClient.SERVICE_HOST:
                return Jmx2LogzioJavaAgent.SERVICE_HOST;
            case JolokiaClient.POLLING_INTERVAL_IN_SEC:
                return Jmx2LogzioJavaAgent.METRICS_POLLING_INTERVAL;
            case JolokiaClient.FROM_DISK:
                return JavaAgentClient.FROM_DISK;
            case JolokiaClient.IN_MEMORY_QUEUE_CAPACITY:
                return JavaAgentClient.IN_MEMORY_QUEUE_CAPACITY;
            case JolokiaClient.LOGS_COUNT_LIMIT:
                return JavaAgentClient.LOGS_COUNT_LIMIT;
            case JolokiaClient.DISK_SPACE_CHECKS_INTERVAL:
                return JavaAgentClient.DISK_SPACE_CHECK_INTERVAL;
            case JolokiaClient.QUEUE_DIR:
                return JavaAgentClient.QUEUE_DIR;
            case JolokiaClient.FILE_SYSTEM_SPACE_LIMIT:
                return JavaAgentClient.FILE_SYSTEM_SPACE_LIMIT;
            case JolokiaClient.CLEAN_SENT_METRICS_INTERVAL:
                return JavaAgentClient.CLEAN_SENT_METRICS_INTERVAL;
            default:
                throw new IllegalConfiguration("Unknown configuration option: " + key);
        }
    }
}
