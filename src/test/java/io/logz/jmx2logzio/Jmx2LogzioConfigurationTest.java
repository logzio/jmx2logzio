package io.logz.jmx2logzio;

import ch.qos.logback.classic.Logger;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import io.logz.jmx2logzio.configuration.Jmx2LogzioConfiguration;
import io.logz.jmx2logzio.exceptions.IllegalConfiguration;
import io.logz.jmx2logzio.objects.Dimension;
import io.logz.jmx2logzio.objects.LogzioJavaSenderParams;
import org.apache.commons.io.FileUtils;
import org.junit.Before;
import org.slf4j.LoggerFactory;
import org.testng.Assert;
import org.testng.annotations.AfterTest;
import org.testng.annotations.Test;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Map;

public class Jmx2LogzioConfigurationTest {

    public static final String METRICS_TEST_DIR = "testMetrics";
    private static final String IN_MEMORY_TEST_ARGUMENTS = "LOGZIO_TOKEN=LogzioToken,SERVICE_NAME=CustomServiceName,SERVICE_HOST=CustomServiceHost,FROM_DISK=false,LISTENER_URL=http://listener.com:2222," +
            "WHITE_LIST_REGEX=anything.with(a|b),BLACK_LIST_REGEX=except.you$,POLLING_INTERVAL_IN_SEC=12,IN_MEMORY_QUEUE_CAPACITY=128000000,LOGS_COUNT_LIMIT=150," +
            "DISK_SPACE_CHECKS_INTERVAL=13,QUEUE_DIR=testMetrics,FILE_SYSTEM_SPACE_LIMIT=80,CLEAN_SENT_METRICS_INTERVAL=14";
    private static final String FROM_DISK_TEST_ARGUMENTS = "LOGZIO_TOKEN=LogzioToken,SERVICE_NAME=CustomServiceName,FROM_DISK=true," +
            "DISK_SPACE_CHECKS_INTERVAL=13,QUEUE_DIR=testMetrics,FILE_SYSTEM_SPACE_LIMIT=80,CLEAN_SENT_METRICS_INTERVAL=14";
    private static final String MINIMAL_TEST_CONFIGURATION_ARGUMENTS = "LISTENER_URL=http://127.0.0.1:8070,LOGZIO_TOKEN=LogzioToken,SERVICE_NAME=com.yog.examplerunningapp,QUEUE_DIR=" + METRICS_TEST_DIR;
    private static final String WHITE_LIST_ARGUMENT_CONFIGURATION = "LISTENER_URL=http://127.0.0.1:8070,LOGZIO_TOKEN=LogzioToken,SERVICE_NAME=com.yog.examplerunningapp,WHITE_LIST_REGEX=.*MemoryUsagePercent.*,QUEUE_DIR="+ METRICS_TEST_DIR;
    private static final String BLACK_LIST_ARGUMENT_CONFIGURATION = "LISTENER_URL=http://127.0.0.1:8070,LOGZIO_TOKEN=LogzioToken,SERVICE_NAME=com.yog.examplerunningapp,BLACK_LIST_REGEX=.*Max.*,QUEUE_DIR=" + METRICS_TEST_DIR;
    private static final String EXTRA_DIMENSIONS_ARGUMENT_CONFIGURATION = "LISTENER_URL=http://127.0.0.1:8070,LOGZIO_TOKEN=LogzioToken,SERVICE_NAME=com.yog.examplerunningapp,EXTRA_DIMENSIONS={origin=local:framework=spring},QUEUE_DIR=" + METRICS_TEST_DIR;
    private static Logger logger;

    private static Config getIntegratedConfiguration(String agentArgument) {

        Map<String, String> configurationMap = Jmx2LogzioJavaAgent.parseArgumentsString(agentArgument);

        if (configurationMap.get(Jmx2LogzioJavaAgent.getArgumentConfigurationRepresentation("SERVICE_NAME")) == null) {
            throw new IllegalConfiguration("SERVICE_NAME must be one of the arguments");
        }
        if (configurationMap.get(Jmx2LogzioJavaAgent.getArgumentConfigurationRepresentation("LOGZIO_TOKEN")) == null) {
            throw new IllegalConfiguration("LOGZIO_TOKEN must be one of the arguments");
        }

        Config userConfig = ConfigFactory.parseMap(configurationMap);
        Config fileConfig = ConfigFactory.load("javaagent.conf");

        // Merge the two configurations
        return userConfig.withFallback(fileConfig);
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

    @Before
    private void setup() {
        logger = (Logger) LoggerFactory.getLogger(Jmx2LogzioConfigurationTest.class);
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
    public void inMemoryConfigurationArgumentsParsingTest() {
        String testArguments = IN_MEMORY_TEST_ARGUMENTS;
        Jmx2LogzioConfiguration configuration = new Jmx2LogzioConfiguration(getIntegratedConfiguration(testArguments));
        LogzioJavaSenderParams senderParams = configuration.getSenderParams();

        Assert.assertEquals(senderParams.getToken(),"LogzioToken");
        Assert.assertEquals(configuration.getServiceName(),"CustomServiceName");
        Assert.assertEquals(configuration.getServiceHost(),"CustomServiceHost");
        Assert.assertFalse(senderParams.isFromDisk());
        Assert.assertEquals(senderParams.getUrl(),"http://listener.com:2222");
        Assert.assertEquals(configuration.getWhiteListPattern().pattern(),"anything.with(a|b)");
        Assert.assertEquals(configuration.getBlackListPattern().pattern(),"except.you$");
        Assert.assertEquals(configuration.getMetricsPollingIntervalInSeconds(),12);
        Assert.assertEquals(senderParams.getInMemoryQueueCapacityInBytes(),128000000);
        Assert.assertEquals(senderParams.getLogsCountLimit(),150);

    }

    @Test
    public void fromDiskConfigurationArgumentsParsingTest() {
        String testArguments = FROM_DISK_TEST_ARGUMENTS;
        Jmx2LogzioConfiguration configuration = new Jmx2LogzioConfiguration(getIntegratedConfiguration(testArguments));
        LogzioJavaSenderParams senderParams = configuration.getSenderParams();

        Assert.assertTrue(senderParams.isFromDisk());
        Assert.assertEquals(senderParams.getDiskSpaceCheckInterval(),13);
        String parent = senderParams.getQueueDir().getParent() == null ? "" : senderParams.getQueueDir().getParent() + "/";
        Assert.assertEquals(parent + senderParams.getQueueDir().getName(),METRICS_TEST_DIR);
        Assert.assertEquals(senderParams.getFileSystemFullPercentThreshold(),80);
        Assert.assertEquals(senderParams.getGcPersistedQueueFilesIntervalSeconds(),14);
    }

    @Test
    public void extraDimensionsArgumentParsingTest() {
        String testArguments = EXTRA_DIMENSIONS_ARGUMENT_CONFIGURATION;
        Jmx2LogzioConfiguration configuration = new Jmx2LogzioConfiguration(getIntegratedConfiguration(testArguments));
        List<Dimension> extraDimensions = configuration.getExtraDimensions();

        Assert.assertEquals(extraDimensions.size(), 2);
        Assert.assertEquals(extraDimensions.get(0).getKey(),"origin");
        Assert.assertEquals(extraDimensions.get(0).getValue(), "local");
    }


}
