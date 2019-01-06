package io.logz.jmx2logzio;

import io.logz.jmx2logzio.Utils.MetricsPipeline;
import io.logz.jmx2logzio.objects.JavaAgentClient;
import io.logz.jmx2logzio.clients.JolokiaClient;
import io.logz.jmx2logzio.configuration.Jmx2LogzioConfiguration;
import io.logz.jmx2logzio.exceptions.IllegalConfiguration;
import io.logz.jmx2logzio.objects.MBeanClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import static io.logz.jmx2logzio.configuration.Jmx2LogzioConfiguration.MetricClientType.JOLOKIA;
import static io.logz.jmx2logzio.configuration.Jmx2LogzioConfiguration.MetricClientType.MBEAN_PLATFORM;

public class Jmx2Logzio {

    private static final Logger logger = LoggerFactory.getLogger(Jmx2Logzio.class);

    private final Jmx2LogzioConfiguration conf;
    private final ScheduledThreadPoolExecutor taskScheduler;
    private final MBeanClient client;

    public Jmx2Logzio(Jmx2LogzioConfiguration conf) {
        this.conf = conf;

        this.taskScheduler = new ScheduledThreadPoolExecutor(1);

        if (conf.getMetricClientType() == JOLOKIA) {
            this.client = new JolokiaClient(conf.getJolokiaFullUrl());
            logger.info("Running with Jolokia URL: {}", conf.getJolokiaFullUrl());

        } else if (conf.getMetricClientType() == MBEAN_PLATFORM) {
            this.client = new JavaAgentClient();
            logger.info("Running with Mbean client");
        }
        else {
            throw new IllegalConfiguration("Unsupported client type: " + conf.getMetricClientType());
        }
    }

    public void run() {
        logger.info("java sender: url = {}, token = {}", conf.getSender().getUrl(), conf.getSender().getToken());
        enableHangupSupport();
        MetricsPipeline pipeline = new MetricsPipeline(conf, client);
        long initialDelay = calcDurationInSecondsUntilNextPollingIntervalStartTime();
        taskScheduler.scheduleAtFixedRate(pipeline::pollAndSend, initialDelay, conf.getMetricsPollingIntervalInSeconds(), TimeUnit.SECONDS);
    }

    private long calcDurationInSecondsUntilNextPollingIntervalStartTime() {
        long now = TimeUnit.MILLISECONDS.toSeconds(System.currentTimeMillis());
        return conf.getMetricsPollingIntervalInSeconds() - (now % conf.getMetricsPollingIntervalInSeconds()) + 1;
    }

    private void shutdown() {
        logger.info("Shutting down...");
        try {
            taskScheduler.shutdown();
            taskScheduler.awaitTermination(20, TimeUnit.SECONDS);
            taskScheduler.shutdownNow();
        } catch (InterruptedException e) {

            Thread.interrupted();
            taskScheduler.shutdownNow();
        }
    }

    /**
     * Enables the hangup support. Gracefully stops by calling shutdown() on a
     * Hangup signal.
     */
    private void enableHangupSupport() {
        HangupInterceptor interceptor = new HangupInterceptor(this);
        Runtime.getRuntime().addShutdownHook(interceptor);
    }

    /**
     * A class for intercepting the hang up signal and do a graceful shutdown of the Camel.
     */
    private static final class HangupInterceptor extends Thread {
        private Logger logger = LoggerFactory.getLogger(HangupInterceptor.class);
        private Jmx2Logzio main;

        public HangupInterceptor(Jmx2Logzio main) {
            this.main = main;
        }

        @Override
        public void run() {
            logger.info("Received hang up - stopping...");
            try {
                main.shutdown();
            } catch (Exception ex) {
                logger.warn("Error during stopping main", ex);
            }
        }
    }
}
