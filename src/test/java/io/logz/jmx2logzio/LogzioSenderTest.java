package io.logz.jmx2logzio;

import io.logz.jmx2logzio.clients.ListenerWriter;
import io.logz.jmx2logzio.configuration.Jmx2LogzioConfiguration;
import io.logz.jmx2logzio.objects.Dimension;
import io.logz.jmx2logzio.objects.Metric;
import org.mockserver.client.MockServerClient;
import org.mockserver.integration.ClientAndServer;
import org.mockserver.model.HttpRequest;
import org.testng.Assert;
import org.testng.annotations.AfterTest;
import org.testng.annotations.BeforeTest;
import org.testng.annotations.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static java.lang.Thread.sleep;
import static org.mockserver.integration.ClientAndServer.startClientAndServer;
import static org.mockserver.model.HttpRequest.request;
import static org.mockserver.model.HttpResponse.response;

public class LogzioSenderTest {

    private Jmx2LogzioConfiguration config;
    private ClientAndServer mockServer;
    private HttpRequest[] recordedRequests;
    MockServerClient mockServerClient = null;

    @BeforeTest
    private void startMock() {
        mockServer = startClientAndServer(8070);

        mockServerClient = new MockServerClient("localhost", 8070);
        mockServerClient
                .when(request().withMethod("POST"))
                .respond(response().withStatusCode(200));
    }

    private void waitToSend() {
        System.out.println("waiting...");
        try {
            sleep(10000);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    @Test
    public void sendMetricTest() {
        config = Jmx2LogzioConfigurationTest.getMinimalTestConfiguration();
        String key = "The-Answer-To-Life-The-Universe-And-Everything";
        Number value = 42;
        List<Dimension> dimensions = new ArrayList<>();
        dimensions.add(0, new Dimension("type", "myType"));
        List<Metric> metrics = new ArrayList<>();
        metrics.add(new Metric(key, value, Instant.now(), dimensions));

        ListenerWriter writer = new ListenerWriter(config);
        writer.writeMetrics(metrics);
        waitToSend();

        recordedRequests = mockServerClient.retrieveRecordedRequests(request().withMethod("POST"));
        Assert.assertEquals(recordedRequests.length, 1);
        String message = recordedRequests[0].getBodyAsString();
        Assert.assertTrue(message.contains("\"" + key + "\":" + value));
    }

    @AfterTest
    public void stopMockServer() {
        mockServer.stop();
    }
}
