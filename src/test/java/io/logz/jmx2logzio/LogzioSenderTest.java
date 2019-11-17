package io.logz.jmx2logzio;

import io.logz.jmx2logzio.clients.ListenerWriter;
import io.logz.jmx2logzio.configuration.Jmx2LogzioConfiguration;
import io.logz.jmx2logzio.objects.Dimension;
import io.logz.jmx2logzio.objects.Metric;
import org.apache.commons.io.FileUtils;
import org.mockserver.client.MockServerClient;
import org.mockserver.integration.ClientAndServer;
import org.mockserver.model.HttpRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.Assert;
import org.testng.annotations.AfterTest;
import org.testng.annotations.BeforeTest;
import org.testng.annotations.Test;

import java.io.File;
import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.mockserver.integration.ClientAndServer.startClientAndServer;
import static org.mockserver.model.HttpRequest.request;
import static org.mockserver.model.HttpResponse.response;

public class LogzioSenderTest {

    private final Logger logger = LoggerFactory.getLogger(LogzioSenderTest.class);
    private Jmx2LogzioConfiguration config;
    private ClientAndServer mockServer;
    private HttpRequest[] recordedRequests;
    private MockServerClient mockServerClient = null;

    @BeforeTest
    private void startMockServer() {
        logger.debug("starting mock server");
        mockServer = startClientAndServer(8070);

        mockServerClient = new MockServerClient("localhost", 8070);
        mockServerClient
                .when(request().withMethod("POST"))
                .respond(response().withStatusCode(200));
    }

    @AfterTest
    public void stopMockServer() {
        logger.info("stoping mock server...");
        mockServer.stop();
    }


    @AfterTest
    private void clean() {
        try {
            FileUtils.deleteDirectory(new File(Jmx2LogzioConfigurationTest.getShaQueueDir()));
        } catch (IOException e) {
            logger.error("couldn't remove temp metrics directory {}: {}", Jmx2LogzioConfigurationTest.getShaQueueDir(), e.getMessage(), e);
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
        Map<String, Number> metricsNamesToValues = new HashMap<>();
        metricsNamesToValues.put(key, value);
        metrics.add(new Metric(metricsNamesToValues, Instant.now(), dimensions));

        ListenerWriter writer = new ListenerWriter(config);
        writer.writeMetrics(metrics);
        writer.getSender().drainQueueAndSend();
        recordedRequests = mockServerClient.retrieveRecordedRequests(request().withMethod("POST"));
        Assert.assertEquals(recordedRequests.length, 1);
        String message = recordedRequests[0].getBodyAsString();
        Assert.assertTrue(message.contains("\"" + key + "\":" + value));
        writer.shutdown();
    }

}
