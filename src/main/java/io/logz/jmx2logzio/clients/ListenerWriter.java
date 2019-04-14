package io.logz.jmx2logzio.clients;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.util.concurrent.ThreadFactoryBuilder;
import io.logz.jmx2logzio.Jmx2Logzio;
import io.logz.jmx2logzio.Utils.HangupInterceptor;
import io.logz.jmx2logzio.Utils.Shutdownable;
import io.logz.jmx2logzio.configuration.Jmx2LogzioConfiguration;
import io.logz.jmx2logzio.objects.LogzioJavaSenderParams;
import io.logz.jmx2logzio.objects.Metric;
import io.logz.jmx2logzio.objects.StatusReporterFactory;
import io.logz.sender.HttpsRequestConfiguration;
import io.logz.sender.LogzioSender;
import io.logz.sender.SenderStatusReporter;
import io.logz.sender.exceptions.LogzioParameterErrorException;

import ch.qos.logback.classic.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.PostConstruct;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.*;

import static java.util.concurrent.Executors.newSingleThreadScheduledExecutor;

public class ListenerWriter implements Shutdownable {
    private final Logger logger = (Logger) LoggerFactory.getLogger(ListenerWriter.class);

    private final static ObjectMapper mapper = new ObjectMapper();
    private final BlockingQueue<Metric> messageQueue;
    private final ScheduledExecutorService scheduledExecutorService;
    private HttpsRequestConfiguration requestConf;
    private final LogzioJavaSenderParams logzioSenderParams;
    private final LogzioSender logzioSender;

    public ListenerWriter(Jmx2LogzioConfiguration requestConf) {
        this.logzioSenderParams = requestConf.getSenderParams();
        this.logzioSender = getLogzioSender();
        this.logzioSender.start();
        this.messageQueue = new LinkedBlockingQueue<>();
        this.scheduledExecutorService = newSingleThreadScheduledExecutor(new ThreadFactoryBuilder().setNameFormat("Jmx2ListenerWriter-%d").build());
    }

    /**
     * Create a logz.io java sender with the received configuration
     * @return LogzioSender object
     */
    private LogzioSender getLogzioSender() {

        try {
            requestConf = HttpsRequestConfiguration
                    .builder()
                    .setLogzioListenerUrl(logzioSenderParams.getUrl())
                    .setLogzioType(logzioSenderParams.getType())
                    .setLogzioToken(logzioSenderParams.getToken())
                    .setCompressRequests(logzioSenderParams.isCompressRequests())
                    .build();
        } catch (LogzioParameterErrorException e) {
            logger.error("problem in one or more parameters with error {}", e.getMessage());
        }
        SenderStatusReporter statusReporter = StatusReporterFactory.newSenderStatusReporter(logger);
        LogzioSender.Builder senderBuilder = LogzioSender
                .builder();
        senderBuilder.setTasksExecutor(Executors.newScheduledThreadPool(logzioSenderParams.getThreadPoolSize()));
        senderBuilder.setReporter(statusReporter);
        senderBuilder.setHttpsRequestConfiguration(requestConf);
        senderBuilder.setDebug(logzioSenderParams.isDebug());

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
            logger.error("problem in one or more parameters with error {}", e.getMessage());
        }
        return null;
    }

    /**
     * Add metrics the sender to be sent
     * @param metrics a list of metrics to be sent
     */
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

    /**
     * Start a scheduled task to poll the metrics from the metrics queue and send them
     */
    @PostConstruct
    public void start() {
        scheduledExecutorService.scheduleWithFixedDelay(this::trySendQueueToListener, 0, 5, TimeUnit.SECONDS);
        enableHangupSupport();
    }

    private void trySendQueueToListener() {
        try {
            Metric metric = messageQueue.take();
            writeMetrics(Arrays.asList(metric));
        } catch (InterruptedException e) {
            logger.error("error enqueueing metrics, got interrupt: {}", e.getMessage());
        }
    }

    @Override
    public void shutdown() {
        logger.info("Closing Listener Writer...");
        logzioSender.stop();
        scheduledExecutorService.shutdownNow();
    }

    /**
     * Enable graceful shutdown
     */
    private void enableHangupSupport() {
        HangupInterceptor interceptor = new HangupInterceptor(this);
        Runtime.getRuntime().addShutdownHook(interceptor);
    }
}
