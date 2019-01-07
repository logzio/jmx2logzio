package io.logz.jmx2logzio.clients;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.util.concurrent.ThreadFactoryBuilder;
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

//import org.apache.kafka.clients.producer.KafkaProducer;
//import org.apache.kafka.clients.producer.ProducerRecord;

public class ListenerWriter {
    private static final Logger logger = LoggerFactory.getLogger(ListenerWriter.class);

//    private final KafkaProducer<String, String> kafkaProducer;
    private final static ObjectMapper mapper = new ObjectMapper();
    private final BlockingQueue<Metric> messageQueue;
    private ScheduledExecutorService scheduledExecutorService;
    private HttpsRequestConfiguration requestConf;
    private LogzioJavaSenderParams logzioSenderParams;
    private LogzioSender logzioSender;

    public ListenerWriter(Jmx2LogzioConfiguration requestConf) {

        this.logzioSenderParams = requestConf.getSenderParams();
//        this.kafkaProducer = createKafkaProducer(requestConf.getProducerProperties());
        initLogzioSender();

//        int queueCapacity = requestConf.getQueueCapacity();
        messageQueue = new LinkedBlockingQueue<>();

        scheduledExecutorService = Executors.newScheduledThreadPool (1,
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
        SenderStatusReporter statusReporter = new SenderStatusReporter() {
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
            logger.error("promblem in one or more parameters with error {}", e.getMessage()); //todo: add parameters string
        }

    }

//    String params = String.format("listener url {}, logzio type {}, logzio token {}",this.listenerUrl,this.logzioType,this.token);
//            throw new LogzioParameterErrorException(params, "one or more incorrect parameters");

//    private KafkaProducer<String, String> createKafkaProducer(Properties producerProperties) {
//        Properties properties = new Properties();
//        properties.putAll(producerProperties);
//        return new KafkaProducer<>(properties);
//    }

    public void writeMetrics(List<Metric> metrics){
        metrics.stream().forEach(metric -> {
            String jsonStringMetric = convertToJson(metric);
            byte[] metricAsBytes = java.nio.charset.StandardCharsets.UTF_8.encode(jsonStringMetric).array();
            logzioSender.send(metricAsBytes);
//            ProducerRecord<String, String> record = new ProducerRecord<>(kafkaTopic, jsonMetric);
//            kafkaProducer.send(record, (metadata, exception) -> onWriteFinish(metric, exception));
        });
    }

//    private void onWriteFinish(Metric metric, Exception exception) {
//        if (exception != null) {
//            logger.warn("failed to write metric to kafka topic {} with exception {} message queue size {}", kafkaTopic, exception.getMessage(), messageQueue.size());
//            try {
//                messageQueue.put(metric);
//            } catch (InterruptedException e) {
//                logger.error("failed to write metric {} to buffer queue with exception {}", convertToJson(metric), e.getMessage());
//            }
//        } else {
//            // success
//            logger.debug("successfully write metric to kafka topic {} ", kafkaTopic);
//        }
//    }

    private String convertToJson(Metric metric) {
        try {
            return mapper.writeValueAsString(metric);
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
    }

    @PostConstruct
    public void start(){
        scheduledExecutorService.scheduleWithFixedDelay(this::trySendQueueToListener, 0, 5, TimeUnit.SECONDS);
    }

    private void trySendQueueToListener() {
        try {
            Metric metric = messageQueue.take();
            writeMetrics(Arrays.asList(metric));
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }
}
