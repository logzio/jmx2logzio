package io.logz.jmx2logzio.clients;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.util.concurrent.ThreadFactoryBuilder;
import io.logz.jmx2logzio.Utils.HangupInterceptor;
import io.logz.jmx2logzio.Utils.Shutdownable;
import io.logz.jmx2logzio.configuration.Jmx2LogzioConfiguration;
import io.logz.jmx2logzio.objects.LogzioJavaSenderParams;
import io.logz.jmx2logzio.objects.Metric;
import io.logz.sender.HttpsRequestConfiguration;
import io.logz.sender.LogzioSender;
import io.logz.sender.SenderStatusReporter;
import io.logz.sender.exceptions.LogzioParameterErrorException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.PostConstruct;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.*;

public class ListenerWriter implements Shutdownable {
    private static final Logger logger = LoggerFactory.getLogger(ListenerWriter.class);

    private final static ObjectMapper mapper = new ObjectMapper();
    private final BlockingQueue<Metric> messageQueue;
    private ScheduledExecutorService scheduledExecutorService;
    private HttpsRequestConfiguration requestConf;
    private LogzioJavaSenderParams logzioSenderParams;
    private LogzioSender logzioSender;

    public ListenerWriter(Jmx2LogzioConfiguration requestConf) {

        this.logzioSenderParams = requestConf.getSenderParams();
        initLogzioSender();
        messageQueue = new LinkedBlockingQueue<>();

        scheduledExecutorService = Executors.newScheduledThreadPool(1,
                new ThreadFactoryBuilder().setNameFormat("Jmx2ListenerWriter-%d").build());
    }

    private void initLogzioSender() {

        try {
            this.requestConf = HttpsRequestConfiguration
                    .builder()
                    .setLogzioListenerUrl(logzioSenderParams.getUrl())
                    .setLogzioType(logzioSenderParams.getType())
                    .setLogzioToken(logzioSenderParams.getToken())
                    .setCompressRequests(logzioSenderParams.isCompressRequests())
                    .build();
        } catch (LogzioParameterErrorException e) {
            logger.error("promblem in one or more parameters with error {}", e.getMessage()); //todo: add parameters string
        }
        SenderStatusReporter statusReporter = getStatusReporter();
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
            this.logzioSender = senderBuilder.build();
        } catch (LogzioParameterErrorException e) {
            logger.error("promblem in one or more parameters with error {}", e.getMessage());
        }
        this.logzioSender.start();
    }

    private SenderStatusReporter getStatusReporter() {
        return new SenderStatusReporter() {
            @Override
            public void error(String s) {
                logger.error(s);
            }

            @Override
            public void error(String s, Throwable throwable) {
                logger.error(s);
            }

            @Override
            public void warning(String s) {
                logger.warn(s);
            }

            @Override
            public void warning(String s, Throwable throwable) {
                logger.warn(s);
            }

            @Override
            public void info(String s) {
                logger.info(s);
            }

            @Override
            public void info(String s, Throwable throwable) {
                logger.info(s);
            }
        };
    }

    public void writeMetrics(List<Metric> metrics) {
        logger.info("sending {} metrics", metrics.size());
        metrics.stream().forEach(metric -> {
            String jsonStringMetric = convertToJson(metric);
            byte[] metricAsBytes = java.nio.charset.StandardCharsets.UTF_8.encode(jsonStringMetric).array();
            logzioSender.send(metricAsBytes);
        });
    }


    private String convertToJson(Metric metric) {
        try {
            return mapper.writeValueAsString(metric);
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
    }

    @PostConstruct
    public void start() {
        scheduledExecutorService.scheduleWithFixedDelay(this::trySendQueueToListener, 0, 5, TimeUnit.SECONDS);
        enableHangupSupport();
    }

    private void trySendQueueToListener() {
        try {
            Metric metric = messageQueue.take();
            writeMetrics(Arrays.asList(metric));
        } catch (Exception e) {
            logger.error("error enqueueing metrics: {}", e.getMessage());
        }
    }

    @Override
    public void shutdown() {
        logger.info("Closing Listener Writer...");
        try {
            scheduledExecutorService.shutdown();
            scheduledExecutorService.awaitTermination(20, TimeUnit.SECONDS);
            scheduledExecutorService.shutdownNow();
        } catch (InterruptedException e) {
            logger.error("couldn't shutdown the writer properly: {}", e.getMessage());
            Thread.interrupted();
            scheduledExecutorService.shutdownNow();
        }
        logzioSender.stop();
    }

    private void enableHangupSupport() {
        HangupInterceptor interceptor = new HangupInterceptor(this);
        Runtime.getRuntime().addShutdownHook(interceptor);
    }
}
