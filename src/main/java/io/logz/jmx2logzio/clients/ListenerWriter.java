package io.logz.jmx2logzio.clients;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.PostConstruct;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;


public class ListenerWriter implements Shutdownable {
    private final Logger logger = LoggerFactory.getLogger(ListenerWriter.class);

    private final static ObjectMapper mapper = new ObjectMapper();
    private HttpsRequestConfiguration requestConf;
    private final LogzioJavaSenderParams logzioSenderParams;
    private final LogzioSender logzioSender;
    private ScheduledExecutorService senderExecutors;

    public ListenerWriter(Jmx2LogzioConfiguration requestConf) {
        this.logzioSenderParams = requestConf.getSenderParams();
        this.logzioSender = getLogzioSender();
        this.logzioSender.start();
    }

    /**
     * Create a logz.io java sender with the received configuration
     * @return LogzioSender object
     */
    private LogzioSender getLogzioSender() {

        senderExecutors = Executors.newScheduledThreadPool(logzioSenderParams.getThreadPoolSize());
        try {
            requestConf = HttpsRequestConfiguration
                    .builder()
                    .setLogzioListenerUrl(logzioSenderParams.getUrl())
                    .setLogzioType(logzioSenderParams.getType())
                    .setLogzioToken(logzioSenderParams.getToken())
                    .setCompressRequests(logzioSenderParams.isCompressRequests())
                    .build();
        } catch (LogzioParameterErrorException e) {
            logger.error("problem in one or more parameters with error {}", e.getMessage(), e);
        }
        SenderStatusReporter statusReporter = StatusReporterFactory.newSenderStatusReporter(LoggerFactory.getLogger(LogzioSender.class));
        LogzioSender.Builder senderBuilder = LogzioSender
                .builder();
        senderBuilder.setTasksExecutor(senderExecutors);
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
            logger.error("problem in one or more parameters with error {}", e.getMessage(), e);
        }
        return null;
    }

    /**
     * Add metrics the sender to be sent
     * @param metrics a list of metrics to be sent
     */
    public void writeMetrics(List<Metric> metrics) {
        logger.debug("sending {} metrics", metrics.size());
        metrics.stream().forEach(metric -> {
            String jsonStringMetric = convertToJson(metric);
            byte[] metricAsBytes = java.nio.charset.StandardCharsets.UTF_8.encode(jsonStringMetric).array();
            logzioSender.send(metricAsBytes);
        });
    }


    public LogzioSender getSender() {
        return logzioSender;
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
        enableHangupSupport();
    }

    @Override
    public void shutdown() {
        logzioSender.stop();
        senderExecutors.shutdown();
        try {
            senderExecutors.awaitTermination(20, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            logger.error("Shutdown was interrupted {}", e.getMessage(), e);
        }
        if (!senderExecutors.isTerminated()) {
            senderExecutors.shutdownNow();
        }
        logger.info("Closing Listener Writer...");
    }

    /**
     * Enable graceful shutdown
     */
    private void enableHangupSupport() {
        HangupInterceptor interceptor = new HangupInterceptor(this);
        Runtime.getRuntime().addShutdownHook(interceptor);
    }
}
