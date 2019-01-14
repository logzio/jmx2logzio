package io.logz.jmx2logzio;

import io.logz.jmx2logzio.clients.ListenerWriter;
import io.logz.jmx2logzio.configuration.Jmx2LogzioConfiguration;
import io.logz.jmx2logzio.objects.Dimension;
import io.logz.jmx2logzio.objects.LogzioJavaSenderParams;
import io.logz.jmx2logzio.objects.Metric;
import io.logz.sender.HttpsRequestConfiguration;
import io.logz.sender.LogzioSender;
import io.logz.sender.SenderStatusReporter;
import io.logz.sender.com.google.gson.JsonObject;
import io.logz.sender.exceptions.LogzioParameterErrorException;
import org.mockserver.client.MockServerClient;
import org.mockserver.integration.ClientAndServer;
import org.testng.annotations.*;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;

import static java.lang.Thread.sleep;
import static org.mockserver.integration.ClientAndServer.startClientAndServer;
import static org.mockserver.model.HttpRequest.request;
import static org.mockserver.model.HttpResponse.response;

public class LogzioSenderTest {

    Jmx2LogzioConfiguration config;
    LogzioSender sender;

    private ClientAndServer mockServer;

    @BeforeTest
    private void startMock(){
        mockServer = startClientAndServer(8070);
        new MockServerClient("localhost", 8070)
                .when(
                        request()
                                .withMethod("POST")
                )
                .respond(
                        response()
                                .withStatusCode(200)
                );
    }

    @BeforeMethod
    private void setup(){
        config = Jmx2LogzioConfigurationTest.getMinimalTestConfiguration();
        sender = initLogzioSender();

    }

    @AfterMethod
    private void waitToSend() {
        try {
            sleep(10000);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        System.out.println("End Test.");
    }

    @Test
    public void sendDemoLogTest(){
        JsonObject temp = new JsonObject();
        temp.addProperty("msg","hello logz.io");

        byte[] messageAsBytes = java.nio.charset.StandardCharsets.UTF_8.encode(temp.toString()).array();
        sender.start();
        sender.send(messageAsBytes);
    }

    @Test
    public void sendAListOfMetrics() {
        List<Dimension> dimensions = new ArrayList<>();
        dimensions.add(0,new Dimension("type","myType"));
        List<Metric> metrics = new ArrayList<>();
        metrics.add(new Metric("Sample Metric", 2, Instant.EPOCH,dimensions));
        metrics.add(new Metric("The-Answer-To-Life-The-Universe-And-Everything",42,Instant.now(),dimensions));

        ListenerWriter writer = new ListenerWriter(config);
        writer.writeMetrics(metrics);

    }

    private LogzioSender initLogzioSender() {
        LogzioJavaSenderParams logzioSenderParams = config.getSenderParams();
        HttpsRequestConfiguration requestConf = null;
        try {
            requestConf = HttpsRequestConfiguration
                    .builder()
                    .setLogzioListenerUrl(logzioSenderParams.getUrl())
                    .setLogzioType(logzioSenderParams.getType())
                    .setLogzioToken(logzioSenderParams.getToken())
                    .setCompressRequests(logzioSenderParams.isCompressRequests())
                    .build();
        } catch (LogzioParameterErrorException e) {
            System.out.println("promblem in one or more parameters with error " + e.getMessage()); //todo: add parameters string

        }
        SenderStatusReporter statusReporter = new SenderStatusReporter() {
            @Override
            public void error(String s) {
//                logger.error(s);
                System.out.println("error " +s);
            }

            @Override
            public void error(String s, Throwable throwable) {
//                logger.error(s);
                System.out.println("error " + s);

            }

            @Override
            public void warning(String s) {
//                logger.warn(s);
                System.out.println("warning " + s);
            }

            @Override
            public void warning(String s, Throwable throwable) {
//                logger.warn(s);
                System.out.println("warning " + s);

            }

            @Override
            public void info(String s) {
//                logger.info(s);
                System.out.println("info: " + s);
            }

            @Override
            public void info(String s, Throwable throwable) {
//                logger.info(s);
                System.out.println("info: " + s);

            }
        };
        LogzioSender.Builder senderBuilder = LogzioSender
                .builder()
                .setTasksExecutor(Executors.newScheduledThreadPool(logzioSenderParams.getThreadPoolSize()))
                .setReporter(statusReporter)
                .setHttpsRequestConfiguration(requestConf)
                .setDebug(logzioSenderParams.isDebug());

        if (logzioSenderParams.isFromDisk()) {
            senderBuilder.withDiskQueue()
                    .setQueueDir(logzioSenderParams.getQueueDir())
                    .setCheckDiskSpaceInterval(logzioSenderParams.getDiskSpaceCheckInterval())
                    .setFsPercentThreshold(logzioSenderParams.getFileSystemFullPercentThreshold())
                    .setGcPersistedQueueFilesIntervalSeconds(logzioSenderParams.getGcPersistedQueueFilesIntervalSeconds())
                    .endDiskQueue();
        } else {
            senderBuilder.withInMemoryQueue()
                    .setCapacityInBytes(logzioSenderParams.getInMemoryQueueCapacityInBytes())
                    .setLogsCountLimit(logzioSenderParams.getLogsCountLimit())
                    .endInMemoryQueue();
        }
        try {
           return senderBuilder.build();
        } catch (LogzioParameterErrorException e) {
//            logger.error("promblem in one or more parameters with error {}", e.getMessage()); //todo: add parameters string
        }
        return null;
    }
}
