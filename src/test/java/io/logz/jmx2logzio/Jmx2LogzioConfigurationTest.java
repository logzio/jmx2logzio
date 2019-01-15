package io.logz.jmx2logzio;

import com.google.common.base.Splitter;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import io.logz.jmx2logzio.configuration.Jmx2LogzioConfiguration;
import io.logz.jmx2logzio.exceptions.IllegalConfiguration;
import io.logz.jmx2logzio.objects.LogzioJavaSenderParams;
import org.testng.Assert;
import org.testng.annotations.Test;

import java.io.File;
import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileAttribute;
import java.util.HashMap;
import java.util.Map;

public class Jmx2LogzioConfigurationTest {

    private static final String TEST_ARGUMENTS = "LOGZIO_TOKEN=LogzioToken;SERVICE_NAME=CustomServiceName;SERVICE_HOST=CustomServiceHost;FROM_DISK=false;LISTENER_URL=http://listener.url:port;" +
            "WHITE_LIST_REGEX=anything.with(a|b);BLACK_LIST_REGEX=except.you$;INTERVAL_IN_SEC=12;IN_MEMORY_QUEUE_CAPACITY=128000000;LOGS_COUNT_LIMIT=150;" +
            "DISK_SPACE_CHECKS_INTERVAL=13;QUEUE_DIR=Custom/Metrics/Directory;FILE_SYSTEM_SPACE_LIMIT=80;CLEAN_SENT_METRICS_INTERVAL=14;";
    private static final String MINIMAL_TEST_CONFIGURATION_ARGUMENTS = "LISTENER_URL=http://127.0.0.1:8070;LOGZIO_TOKEN=LogzioToken;SERVICE_NAME=com.yog.examplerunningapp;";
    private static final String WHITE_LIST_ARGUMENT_CONFIGURATION = "LISTENER_URL=http://127.0.0.1:8070;LOGZIO_TOKEN=LogzioToken;SERVICE_NAME=com.yog.examplerunningapp;WHITE_LIST_REGEX=.*MemoryUsagePercent.*;";
    private static final String BLACK_LIST_ARGUMENT_CONFIGURATION = "LISTENER_URL=http://127.0.0.1:8070;LOGZIO_TOKEN=LogzioToken;SERVICE_NAME=com.yog.examplerunningapp;BLACK_LIST_REGEX=.*Max.*;";

    private static Config getIntegratedConfiguration(String agentArgument) {
        Path rootDirectory = FileSystems.getDefault().getPath(".");
        try {
           File tempDir = Files.createTempDirectory(rootDirectory, "").toFile();
           tempDir.deleteOnExit();
           agentArgument += "QUEUE_DIR=" + tempDir.getName();
        } catch (IOException e) {
            e.printStackTrace();
        }

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
        String testArguments = MINIMAL_TEST_CONFIGURATION_ARGUMENTS;
        return new Jmx2LogzioConfiguration(getIntegratedConfiguration(testArguments));
    }

//    public static Jmx2LogzioConfiguration getFromDiskTestConfigurationWithListenerURL() {
//        String testArguments = "LISTENER_URL=http://127.0.0.1:8070;LOGZIO_TOKEN=LogzioToken;FROM_DISK=true;SERVICE_NAME=com.yog.examplerunningapp";
//        return new Jmx2LogzioConfiguration(getIntegratedConfiguration(testArguments));
//    }
//
//    public static Jmx2LogzioConfiguration getInMemoryTestConfiguration() {
//        String testArguments = "LISTENER_URL=http://127.0.0.1:8070;LOGZIO_TOKEN=LogzioToken;SERVICE_NAME=com.yog.examplerunningapp;FROM_DISK=false;LOGS_COUNT_LIMIT=50;";
//        return new Jmx2LogzioConfiguration(getIntegratedConfiguration(testArguments));
//    }
//
//    public static Jmx2LogzioConfiguration getCustomHostRapidMetricsPollingInterval() {
//        String testArguments = "LISTENER_URL=http://127.0.0.1:8070;LOGZIO_TOKEN=LogzioToken;SERVICE_NAME=com.yog.examplerunningapp;SERVICE_HOST=A.PC.IN.NOWHERE;INTERVAL_IN_SEC=5";
//        return new Jmx2LogzioConfiguration(getIntegratedConfiguration(testArguments));
//    }

    public static Jmx2LogzioConfiguration getWhiteListTestConfiguration() {
        String testArguments = WHITE_LIST_ARGUMENT_CONFIGURATION;
        return new Jmx2LogzioConfiguration(getIntegratedConfiguration(testArguments));
    }

    public static Jmx2LogzioConfiguration getBlackListTestConfiguration() {
        String testArguments = BLACK_LIST_ARGUMENT_CONFIGURATION;
        return new Jmx2LogzioConfiguration(getIntegratedConfiguration(testArguments));
    }

    @Test
    public void ConfigurationArgumentsParsingTest() {
        String testArguments = TEST_ARGUMENTS;
        Jmx2LogzioConfiguration configuration = new Jmx2LogzioConfiguration(getIntegratedConfiguration(testArguments));
        LogzioJavaSenderParams senderParams = configuration.getSenderParams();

        Assert.assertEquals(senderParams.getToken(),"LogzioToken");
        Assert.assertEquals(configuration.getServiceName(),"CustomServiceName");
        Assert.assertEquals(configuration.getServiceHost(),"CustomServiceHost");
        Assert.assertFalse(senderParams.isFromDisk());
        Assert.assertEquals(senderParams.getUrl(),"http://listener.url:port");
        Assert.assertEquals(configuration.getWhiteListPattern().pattern(),"anything.with(a|b)");
        Assert.assertEquals(configuration.getBlackListPattern().pattern(),"except.you$");
        Assert.assertEquals(configuration.getMetricsPollingIntervalInSeconds(),12);
        Assert.assertEquals(senderParams.getInMemoryQueueCapacityInBytes(),128000000);
        Assert.assertEquals(senderParams.getLogsCountLimit(),150);
        Assert.assertEquals(senderParams.getDiskSpaceCheckInterval(),13);
        Assert.assertEquals(senderParams.getQueueDir().getParent() + "/" + senderParams.getQueueDir().getName(),"Custom/Metrics/Directory");
        Assert.assertEquals(senderParams.getFileSystemFullPercentThreshold(),80);
        Assert.assertEquals(senderParams.getGcPersistedQueueFilesIntervalSeconds(),14);
    }

}
