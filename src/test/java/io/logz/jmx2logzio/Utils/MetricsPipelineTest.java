package io.logz.jmx2logzio.Utils;

import io.logz.jmx2logzio.Jmx2LogzioConfigurationTest;
import io.logz.jmx2logzio.clients.JavaAgentClient;
import io.logz.jmx2logzio.configuration.Jmx2LogzioConfiguration;
import org.testng.annotations.BeforeTest;
import org.testng.annotations.Test;

import static java.lang.Thread.sleep;

public class MetricsPipelineTest {


    Jmx2LogzioConfiguration jmx2LogzioConfiguration;

    @BeforeTest
    private void setup(){
    }

    @Test
    public void testPollAndSendMinimalConfiguration(){
        testPollAndSend(Jmx2LogzioConfigurationTest.getMinimalTestConfiguration());
    }
    @Test
    public void testPollAndSendFromDiskConfiguration(){
        testPollAndSend(Jmx2LogzioConfigurationTest.getFromDiskTestConfigurationWithListenerURL());
    }
    @Test
    public void testPollAndSendInMemoryConfiguration(){
        testPollAndSend(Jmx2LogzioConfigurationTest.getInMemoryTestConfiguration());
    }

    @Test
    public void testPollAndSendRapidMetricsPollConfiguration(){
        testPollAndSend(Jmx2LogzioConfigurationTest.getCustomHostRapidMetricsPollingInterval());
    }

    public void testPollAndSend(Jmx2LogzioConfiguration configuration) {
        jmx2LogzioConfiguration =configuration;
        JavaAgentClient client = new JavaAgentClient();
        MetricsPipeline metricsPipeline = new MetricsPipeline(jmx2LogzioConfiguration,client);
        metricsPipeline.pollAndSend();

        try {
            sleep(60000);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        System.out.println("End. ");
    }
}