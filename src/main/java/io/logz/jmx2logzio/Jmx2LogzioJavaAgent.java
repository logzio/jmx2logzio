package io.logz.jmx2logzio;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import io.logz.jmx2logzio.configuration.Jmx2LogzioConfiguration;
import io.logz.jmx2logzio.exceptions.IllegalConfiguration;
import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import org.slf4j.LoggerFactory;

import java.lang.instrument.Instrumentation;
import java.util.HashMap;
import java.util.Map;

public class Jmx2LogzioJavaAgent {


    private static final String LISTENER_URL = "LISTENER_URL";
    private static final String WHITE_LIST_REGEX = "WHITE_LIST_REGEX";
    private static final String BLACK_LIST_REGEX = "BLACK_LIST_REGEX";
    private static final String LOGZIO_TOKEN = "LOGZIO_TOKEN";
    private static final String SERVICE_NAME = "SERVICE_NAME";
    private static final String SERVICE_HOST = "SERVICE_HOST";
    private static final String POLLING_INTERVAL_IN_SEC = "POLLING_INTERVAL_IN_SEC";
    private static final String FROM_DISK = "FROM_DISK";
    private static final String IN_MEMORY_QUEUE_CAPACITY = "IN_MEMORY_QUEUE_CAPACITY";
    private static final String LOGS_COUNT_LIMIT = "LOGS_COUNT_LIMIT";
    private static final String DISK_SPACE_CHECKS_INTERVAL = "DISK_SPACE_CHECKS_INTERVAL";
    private static final String QUEUE_DIR = "QUEUE_DIR";
    private static final String FILE_SYSTEM_SPACE_LIMIT = "FILE_SYSTEM_SPACE_LIMIT";
    private static final String CLEAN_SENT_METRICS_INTERVAL = "CLEAN_SENT_METRICS_INTERVAL";
    private static final String EXTRA_DIMENSIONS = "EXTRA_DIMENSIONS";
    private static final String LOG_LEVEL = "LOG_LEVEL";
    private static final Logger logger = (Logger) LoggerFactory.getLogger(Jmx2LogzioJavaAgent.class);
    private static final String JAVA_AGENT_CONFIGURATION_FILE = "javaagent.conf";
    private static final int SPLIT_KEY_VALUE_COUNT_LIMIT = 2;
    private static final int INDEX_OF_KEY = 0;
    private static final int INDEX_OF_VALUE = 1;
    private static final String LOG_LEVEL_DEBUG = "DEBUG";

    public static void premain(String agentArgument, Instrumentation instrument) {

        logger.info("Loading with agentArgument: {}", agentArgument);
        Config finalConfig = getIntegratedConfiguration(agentArgument);
        Level logLevel = Level.WARN;
        if (finalConfig.hasPath(Jmx2LogzioJolokia.LOG_LEVEL)) {
            Level configLogLevel = Level.toLevel(finalConfig.getString(Jmx2LogzioJolokia.LOG_LEVEL)); // If this method fails, it will return Level.DEBUG
            if (configLogLevel.equals(Level.DEBUG) && !finalConfig.getString(Jmx2LogzioJolokia.LOG_LEVEL).equals(LOG_LEVEL_DEBUG)) {
                logger.warn("failed to parse log level configuration, view the Readme file for valid level options. setting log level to default..");
            } else {
                logLevel = configLogLevel;
            }
        }
        ((Logger) LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME)).setLevel(logLevel);
        logger.setLevel(logLevel);

        Jmx2LogzioConfiguration jmx2LogzioConfiguration = new Jmx2LogzioConfiguration(finalConfig);
        Jmx2Logzio main = new Jmx2Logzio(jmx2LogzioConfiguration);
        logger.info("Initiated new java agent based Jmx2Logzio instance");

        try {
            main.run();
        } catch (Throwable e) {
            logger.error("Stopping jmx2logzio Java Agent due to unexpected exception: " + e.getMessage(), e);
        }
    }

    /**
     * Create a config object out of an argument string
     * @param agentArgument Argument String received as a parameter
     * @return Config object
     */
    private static Config getIntegratedConfiguration(String agentArgument) {
        Map<String, String> configurationMap = parseArgumentsString(agentArgument);

        if (configurationMap.get(getArgumentConfigurationRepresentation(SERVICE_NAME)) == null) {
            throw new IllegalConfiguration("SERVICE_NAME must be one of the arguments");
        }
        if (configurationMap.get(getArgumentConfigurationRepresentation(LOGZIO_TOKEN)) == null) {
            throw new IllegalConfiguration("LOGZIO_TOKEN must be one of the arguments");
        }

        Config userConfig = ConfigFactory.parseMap(configurationMap);
        Config fileConfig = ConfigFactory.load(JAVA_AGENT_CONFIGURATION_FILE);

        // Merge the two configurations
        return userConfig.withFallback(fileConfig);
    }

    /**
     * Converts a String of arguments to an configuration map
     * @param arguments String received as a parameter in the form of key=value,key=value...
     * @return a key-value configuration map
     * @throws IllegalConfiguration when the arguments string pattern is malformed
     */
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

    /**
     * Converts java agent client configuration argument to a jolokia client argument representation
     * @param key java agent client key argument string
     * @return Jolokia client key argument string
     * @throws IllegalConfiguration if not found
     */
    public static String getArgumentConfigurationRepresentation(String key) throws IllegalConfiguration {

        switch (key) {
            case LISTENER_URL:
                return Jmx2LogzioJolokia.LISTENER_URL;
            case WHITE_LIST_REGEX:
                return Jmx2LogzioJolokia.WHITE_LIST_REGEX;
            case BLACK_LIST_REGEX:
                return Jmx2LogzioJolokia.BLACK_LIST_REGEX;
            case EXTRA_DIMENSIONS:
                return Jmx2LogzioJolokia.EXTRA_DIMENSIONS;
            case LOGZIO_TOKEN:
                return Jmx2LogzioJolokia.LOGZIO_TOKEN;
            case SERVICE_NAME:
                return Jmx2LogzioJolokia.SERVICE_NAME;
            case SERVICE_HOST:
                return Jmx2LogzioJolokia.SERVICE_HOST;
            case POLLING_INTERVAL_IN_SEC:
                return Jmx2LogzioJolokia.METRICS_POLLING_INTERVAL;
            case FROM_DISK:
                return Jmx2LogzioJolokia.FROM_DISK;
            case IN_MEMORY_QUEUE_CAPACITY:
                return Jmx2LogzioJolokia.IN_MEMORY_QUEUE_CAPACITY;
            case LOGS_COUNT_LIMIT:
                return Jmx2LogzioJolokia.LOGS_COUNT_LIMIT;
            case DISK_SPACE_CHECKS_INTERVAL:
                return Jmx2LogzioJolokia.DISK_SPACE_CHECK_INTERVAL;
            case QUEUE_DIR:
                return Jmx2LogzioJolokia.QUEUE_DIR;
            case FILE_SYSTEM_SPACE_LIMIT:
                return Jmx2LogzioJolokia.FILE_SYSTEM_SPACE_LIMIT;
            case CLEAN_SENT_METRICS_INTERVAL:
                return Jmx2LogzioJolokia.CLEAN_SENT_METRICS_INTERVAL;
            case LOG_LEVEL:
                return Jmx2LogzioJolokia.LOG_LEVEL;
            default:
                throw new IllegalConfiguration("Unknown configuration option: " + key);
        }
    }
}
