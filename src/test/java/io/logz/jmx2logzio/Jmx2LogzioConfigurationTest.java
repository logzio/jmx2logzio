package io.logz.jmx2logzio;

import com.google.common.base.Splitter;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import io.logz.jmx2logzio.clients.JavaAgentClient;
import io.logz.jmx2logzio.clients.JolokiaClient;
import io.logz.jmx2logzio.configuration.Jmx2LogzioConfiguration;
import io.logz.jmx2logzio.exceptions.IllegalConfiguration;
import io.logz.jmx2logzio.objects.LogzioJavaSenderParams;
import org.apache.commons.io.FileUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.Assert;
import org.testng.annotations.AfterTest;
import org.testng.annotations.Test;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

public class Jmx2LogzioConfigurationTest {

    public static final String METRICS_TEST_DIR = "testMetrics";
    private static final String TEST_ARGUMENTS = "LOGZIO_TOKEN=LogzioToken,SERVICE_NAME=CustomServiceName,SERVICE_HOST=CustomServiceHost,FROM_DISK=false,LISTENER_URL=http://listener.url:2222," +
            "WHITE_LIST_REGEX=anything.with(a|b),BLACK_LIST_REGEX=except.you$,POLLING_INTERVAL_IN_SEC=12,IN_MEMORY_QUEUE_CAPACITY=128000000,LOGS_COUNT_LIMIT=150," +
            "DISK_SPACE_CHECKS_INTERVAL=13,QUEUE_DIR=testMetrics,FILE_SYSTEM_SPACE_LIMIT=80,CLEAN_SENT_METRICS_INTERVAL=14";
    private static final String MINIMAL_TEST_CONFIGURATION_ARGUMENTS = "LISTENER_URL=http://127.0.0.1:8070,LOGZIO_TOKEN=LogzioToken,SERVICE_NAME=com.yog.examplerunningapp,QUEUE_DIR=" + METRICS_TEST_DIR;
    private static final String WHITE_LIST_ARGUMENT_CONFIGURATION = "LISTENER_URL=http://127.0.0.1:8070,LOGZIO_TOKEN=LogzioToken,SERVICE_NAME=com.yog.examplerunningapp,WHITE_LIST_REGEX=.*MemoryUsagePercent.*,QUEUE_DIR="+ METRICS_TEST_DIR;
    private static final String BLACK_LIST_ARGUMENT_CONFIGURATION = "LISTENER_URL=http://127.0.0.1:8070,LOGZIO_TOKEN=LogzioToken,SERVICE_NAME=com.yog.examplerunningapp,BLACK_LIST_REGEX=.*Max.*,QUEUE_DIR=" + METRICS_TEST_DIR;
    private static final Logger logger = LoggerFactory.getLogger(Jmx2LogzioConfigurationTest.class);

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
            Map<String, String> keyValues = Splitter.on(',').omitEmptyStrings().withKeyValueSeparator('=').split(arguments);

            keyValues.forEach((k,v) ->
                    argumentsMap.put(getArgumentConfigurationRepresentation(k),v));

            return argumentsMap;

        } catch (IllegalArgumentException e) {
            throw new IllegalConfiguration("error parsing arguments " + e.getMessage());
        }
    }

    private static String getArgumentConfigurationRepresentation(String key) throws IllegalConfiguration {

        switch (key) {
            case JolokiaClient.LISTENER_URL:
                return JavaAgentClient.LISTENER_URL;
            case JolokiaClient.WHITE_LIST_REGEX:
                return Jmx2LogzioJavaAgent.WHITE_LIST_REGEX;
            case JolokiaClient.BLACK_LIST_REGEX:
                return Jmx2LogzioJavaAgent.BLACK_LIST_REGEX;
            case JolokiaClient.LOGZIO_TOKEN:
                return JavaAgentClient.LOGZIO_TOKEN;
            case JolokiaClient.SERVICE_NAME:
                return Jmx2LogzioJavaAgent.SERVICE_NAME;
            case JolokiaClient.SERVICE_HOST:
                return Jmx2LogzioJavaAgent.SERVICE_HOST;
            case JolokiaClient.POLLING_INTERVAL_IN_SEC:
                return Jmx2LogzioJavaAgent.METRICS_POLLING_INTERVAL;
            case JolokiaClient.FROM_DISK:
                return  JavaAgentClient.FROM_DISK;
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

    public static Jmx2LogzioConfiguration getMinimalTestConfiguration() {
        String testArguments = MINIMAL_TEST_CONFIGURATION_ARGUMENTS;
        return new Jmx2LogzioConfiguration(getIntegratedConfiguration(testArguments));
    }

    public static Jmx2LogzioConfiguration getWhiteListTestConfiguration() {
        String testArguments = WHITE_LIST_ARGUMENT_CONFIGURATION;
        return new Jmx2LogzioConfiguration(getIntegratedConfiguration(testArguments));
    }

    public static Jmx2LogzioConfiguration getBlackListTestConfiguration() {
        String testArguments = BLACK_LIST_ARGUMENT_CONFIGURATION;
        return new Jmx2LogzioConfiguration(getIntegratedConfiguration(testArguments));
    }

    @AfterTest
    private void clean() {
        try {
            FileUtils.deleteDirectory(new File(Jmx2LogzioConfigurationTest.METRICS_TEST_DIR));
        } catch (IOException e) {
            logger.error("couldn't remove temp metrics directory " + Jmx2LogzioConfigurationTest.METRICS_TEST_DIR);
        }
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
        Assert.assertEquals(senderParams.getUrl(),"http://listener.url:2222");
        Assert.assertEquals(configuration.getWhiteListPattern().pattern(),"anything.with(a|b)");
        Assert.assertEquals(configuration.getBlackListPattern().pattern(),"except.you$");
        Assert.assertEquals(configuration.getMetricsPollingIntervalInSeconds(),12);
        Assert.assertEquals(senderParams.getInMemoryQueueCapacityInBytes(),128000000);
        Assert.assertEquals(senderParams.getLogsCountLimit(),150);
        Assert.assertEquals(senderParams.getDiskSpaceCheckInterval(),13);
        String parent = senderParams.getQueueDir().getParent() == null ? "" : senderParams.getQueueDir().getParent() + "/";
        Assert.assertEquals(parent + senderParams.getQueueDir().getName(),METRICS_TEST_DIR);
        Assert.assertEquals(senderParams.getFileSystemFullPercentThreshold(),80);
        Assert.assertEquals(senderParams.getGcPersistedQueueFilesIntervalSeconds(),14);
    }


}
