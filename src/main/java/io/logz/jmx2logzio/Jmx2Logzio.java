package io.logz.jmx2logzio;

import io.logz.jmx2logzio.Utils.HangupInterceptor;
import io.logz.jmx2logzio.Utils.MetricsPipeline;
import io.logz.jmx2logzio.Utils.Shutdownable;
import io.logz.jmx2logzio.clients.JavaAgentClient;
import io.logz.jmx2logzio.clients.JolokiaClient;
import io.logz.jmx2logzio.configuration.Jmx2LogzioConfiguration;
import io.logz.jmx2logzio.objects.Dimension;
import io.logz.jmx2logzio.objects.MBeanClient;
import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import static io.logz.jmx2logzio.configuration.Jmx2LogzioConfiguration.MetricClientType.JOLOKIA;
import static java.util.concurrent.Executors.newSingleThreadScheduledExecutor;

public class Jmx2Logzio implements Shutdownable {

    public static Level logLevel = Level.WARN;
    private static final Logger logger = (Logger) LoggerFactory.getLogger(Jmx2Logzio.class);

    private final Jmx2LogzioConfiguration conf;
    private final ScheduledExecutorService taskScheduler;
    private final MBeanClient client;

    public Jmx2Logzio(Jmx2LogzioConfiguration conf) {
        logger.setLevel(Jmx2Logzio.logLevel);
        this.conf = conf;
        this.taskScheduler = newSingleThreadScheduledExecutor();
        this.client = conf.getMetricClientType() == JOLOKIA ? new JolokiaClient(conf.getJolokiaFullUrl()) : new JavaAgentClient();
        List<Dimension> extraDimensions = conf.getExtraDimensions();
        client.setExtraDimensions(extraDimensions);
        String clientString = conf.getMetricClientType() == JOLOKIA ? "Jolokia agent URL: " + conf.getJolokiaFullUrl() : "Mbean client";
        logger.info("Running with {}", clientString);
    }

    /**
     * Run a schedule task which collects both JVM and custom metrics and sends them to logz.io
     */
    public void run() {
        logger.info("java sender: url = {}, token = {}", conf.getSenderParams().getUrl(), conf.getSenderParams().getToken());
        enableHangupSupport();
        MetricsPipeline pipeline = new MetricsPipeline(conf, client);
        long initialDelay = calcDurationInSecondsUntilNextPollingIntervalStartTime();
        taskScheduler.scheduleAtFixedRate(pipeline::pollAndSend, initialDelay, conf.getMetricsPollingIntervalInSeconds(), TimeUnit.SECONDS);
    }

    private long calcDurationInSecondsUntilNextPollingIntervalStartTime() {
        long now = TimeUnit.MILLISECONDS.toSeconds(System.currentTimeMillis());
        return conf.getMetricsPollingIntervalInSeconds() - (now % conf.getMetricsPollingIntervalInSeconds()) + 1;
    }

    @Override
    public void shutdown() {
        logger.info("Shutting down...");
        taskScheduler.shutdownNow();
    }

    /**
     * Enables the hangup support. Gracefully stops by calling shutdown() on a
     * Hangup signal.
     */
    private void enableHangupSupport() {
        HangupInterceptor interceptor = new HangupInterceptor(this);
        Runtime.getRuntime().addShutdownHook(interceptor);
    }
}
