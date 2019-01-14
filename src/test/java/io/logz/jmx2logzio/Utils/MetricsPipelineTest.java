package io.logz.jmx2logzio.Utils;

import io.logz.jmx2logzio.Jmx2LogzioConfigurationTest;
import io.logz.jmx2logzio.clients.JavaAgentClient;
import io.logz.jmx2logzio.configuration.Jmx2LogzioConfiguration;
import org.mockserver.client.MockServerClient;
import org.mockserver.integration.ClientAndServer;
import org.testng.annotations.AfterTest;
import org.testng.annotations.BeforeTest;
import org.testng.annotations.Test;
import static org.mockserver.integration.ClientAndServer.startClientAndServer;

import static java.lang.Thread.sleep;
import static org.mockserver.model.HttpResponse.response;
import static org.mockserver.model.HttpRequest.request;

public class MetricsPipelineTest {


    Jmx2LogzioConfiguration jmx2LogzioConfiguration;
    private ClientAndServer mockServer;

    @BeforeTest
    private void setup(){
        mockServer = startClientAndServer(8070);
        new MockServerClient("localhost", 8070)
                .when(request().withMethod("POST"))
                .respond(response().withStatusCode(200)
                );
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

    @Test
    public void testPollAndSendWhiteListConfiguration(){
        testPollAndSend(Jmx2LogzioConfigurationTest.getWhiteListTestConfiguration());
    }
    @Test
    public void testPollAndSendBlackListConfiguration(){
        testPollAndSend(Jmx2LogzioConfigurationTest.getBlackListTestConfiguration());
    }

    public void testPollAndSend(Jmx2LogzioConfiguration configuration) {
        jmx2LogzioConfiguration =configuration;
        JavaAgentClient client = new JavaAgentClient();
        MetricsPipeline metricsPipeline = new MetricsPipeline(jmx2LogzioConfiguration,client);
        metricsPipeline.pollAndSend();

        try {
            sleep(20000);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        System.out.println("End Test.");
    }

    @AfterTest
    private void stopMock() {
        mockServer.stop();
    }
}