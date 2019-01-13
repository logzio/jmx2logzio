package io.logz.jmx2logzio;

import com.google.common.base.Splitter;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import io.logz.jmx2logzio.configuration.Jmx2LogzioConfiguration;
import io.logz.jmx2logzio.exceptions.IllegalConfiguration;
import io.logz.jmx2logzio.objects.LogzioJavaSenderParams;

import java.util.HashMap;
import java.util.Map;

public class Jmx2LogzioConfigurationTest {

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

            keyValues.forEach((k,v) ->
                    argumentsMap.put(getArgumentConfigurationRepresentation(k),v));

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

    public static Jmx2LogzioConfiguration getMinimalTestConfiguration() {
        String testArguments = "LOGZIO_TOKEN=oCwtQDtWjDOMcHXHGGNrnRgkEMxCDuiO;SERVICE_NAME=com.yog.examplerunningapp";
        return new Jmx2LogzioConfiguration(getIntegratedConfiguration(testArguments));
    }

    public static Jmx2LogzioConfiguration getFromDiskTestConfigurationWithListenerURL() {
        String testArguments = "LISTENER_URL=https://listener.logz.io:8071;LOGZIO_TOKEN=oCwtQDtWjDOMcHXHGGNrnRgkEMxCDuiO;FROM_DISK=true;SERVICE_NAME=com.yog.examplerunningapp";
        return new Jmx2LogzioConfiguration(getIntegratedConfiguration(testArguments));
    }

    public static Jmx2LogzioConfiguration getInMemoryTestConfiguration() {
        String testArguments = "LOGZIO_TOKEN=oCwtQDtWjDOMcHXHGGNrnRgkEMxCDuiO;SERVICE_NAME=com.yog.examplerunningapp;FROM_DISK=false;LOGS_COUNT_LIMIT=50;";
        return new Jmx2LogzioConfiguration(getIntegratedConfiguration(testArguments));
    }

    public static Jmx2LogzioConfiguration getCustomHostRapidMetricsPollingInterval() {
        String testArguments = "LOGZIO_TOKEN=oCwtQDtWjDOMcHXHGGNrnRgkEMxCDuiO;SERVICE_NAME=com.yog.examplerunningapp;SERVICE_HOST=A.PC.IN.NOWHERE;INTERVAL_IN_SEC=5";
        return new Jmx2LogzioConfiguration(getIntegratedConfiguration(testArguments));
    }



}
