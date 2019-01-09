package io.logz.jmx2logzio;

import com.oracle.tools.packager.Log;
import io.logz.jmx2logzio.configuration.Jmx2LogzioConfiguration;
import io.logz.jmx2logzio.objects.LogzioJavaSenderParams;
import io.logz.sender.HttpsRequestConfiguration;
import io.logz.sender.LogzioSender;
import io.logz.sender.SenderStatusReporter;
import io.logz.sender.com.google.gson.JsonObject;
import io.logz.sender.exceptions.LogzioParameterErrorException;
import org.testng.annotations.BeforeTest;
import org.testng.annotations.Test;

import java.util.concurrent.Executors;

import static java.lang.Thread.sleep;

public class LogzioSenderTest {

    Jmx2LogzioConfiguration config;

    @BeforeTest
    private void setup(){
        config = Jmx2LogzioConfigurationTest.getTestConfiguration();
    }

    @Test
    public void sendDemoLog(){
        JsonObject temp = new JsonObject();
        temp.addProperty("msg","hello logz.io");

        byte[] messageAsBytes = java.nio.charset.StandardCharsets.UTF_8.encode(temp.toString()).array();
        LogzioSender sender = initLogzioSender();
        sender.start();
        sender.send(messageAsBytes);
        try {
            sleep(10000);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
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
