package io.logz.jmx2logzio;

import com.google.common.base.Splitter;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import io.logz.jmx2logzio.configuration.Jmx2LogzioConfiguration;
import io.logz.jmx2logzio.exceptions.IllegalConfiguration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.instrument.Instrumentation;
import java.util.HashMap;
import java.util.Map;

/**
 * Created by roiravhon on 6/6/16.
 */
public class Jmx2LogzioJavaAgent {

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

        if (configurationMap.get(getArgumentConfigurationRepresentation("SERVICE_NAME")) == null) {
            throw new IllegalConfiguration("SERVICE_NAME must be one of the arguments");
        }
        if (configurationMap.get(getArgumentConfigurationRepresentation("LOGZIO_TOKEN")) == null) {
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
            case "LISTENER_URL":
                return "logzioJavaSender.url";
            case "WHITE_LIST_REGEX":
                return "service.poller.white-list-regex";
            case "BLACK_LIST_REGEX":
                return "service.poller.black-list-regex";
            case "LOGZIO_TOKEN":
                return "logzioJavaSender.token";
            case "SERVICE_NAME":
                return "service.name";
            case "SERVICE_HOST":
                return "service.host";
            case "INTERVAL_IN_SEC":
                return "metricsPollingIntervalInSeconds";
            case "FROM_DISK":
                return "logzioJavaSender.from-disk";
            case "IN_MEMORY_QUEUE_CAPACITY":
                return "logzioJavaSender.in-memory-queue-capacity";
            case "LOGS_COUNT_LIMIT":
                return "logzioJavaSender.log-count-limit";
            case "DISK_SPACE_CHECKS_INTERVAL":
                return "logzioJavaSender.disk-space-checks-interval";
            case "QUEUE_DIR":
                return "logzioJavaSender.queue-dir";
            case "FILE_SYSTEM_SPACE_LIMIT":
                return "logzioJavaSender.file-system-full-percent-threshold";
            case "CLEAN_SENT_METRICS_INTERVAL":
                return "logzioJavaSender.clean-sent-metrics-interval";
            default:
                throw new IllegalConfiguration("Unknown configuration option: " + key);
        }
    }
}
