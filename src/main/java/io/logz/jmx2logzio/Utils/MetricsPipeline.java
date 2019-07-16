package io.logz.jmx2logzio.Utils;

import com.google.common.base.Stopwatch;
import io.logz.jmx2logzio.MetricBean;
import io.logz.jmx2logzio.clients.ListenerWriter;
import io.logz.jmx2logzio.configuration.Jmx2LogzioConfiguration;
import io.logz.jmx2logzio.objects.Dimension;
import io.logz.jmx2logzio.objects.MBeanClient;
import io.logz.jmx2logzio.objects.Metric;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;
import java.util.stream.Collectors;


public class MetricsPipeline {
    private static final DateTimeFormatter timestampFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSZ").withZone(ZoneId.of("UTC"));
    private final Logger logger = LoggerFactory.getLogger(MetricsPipeline.class);
    private final Pattern beansWhiteListPattern;
    private final Pattern beansBlackListPattern;
    private final List<Dimension> metricsPrefix;
    private final int pollingIntervalSeconds;
    private final ListenerWriter listenerClient;
    private final MBeanClient client;

    public MetricsPipeline(Jmx2LogzioConfiguration conf, MBeanClient client) {
        metricsPrefix = new ArrayList<>();
        listenerClient = new ListenerWriter(conf);
        this.client = client;
        this.pollingIntervalSeconds = conf.getMetricsPollingIntervalInSeconds();
        this.beansWhiteListPattern = conf.getWhiteListPattern();
        this.beansBlackListPattern = conf.getBlackListPattern();
        listenerClient.start();

        String serviceName = conf.getServiceName();
        String serviceHost = conf.getServiceHost();

        if (serviceName != null && !serviceName.isEmpty()) {
            Dimension serviceMap = new Dimension(Metric.SERVICE_NAME, MetricsUtils.sanitizeMetricName(serviceName));
            metricsPrefix.add(serviceMap);
        }

        if (serviceHost != null && !serviceHost.isEmpty()) {
            Dimension serviceHostMap = new Dimension(Metric.SERVICE_HOST, MetricsUtils.sanitizeMetricName(serviceHost, false));
            metricsPrefix.add(serviceHostMap);
        }

    }

    private List<Metric> poll() {
        try {
            Instant pollingWindowStart = getPollingWindowStart();
            Stopwatch sw = Stopwatch.createStarted();
            List<MetricBean> beans = client.getBeans();
            if (beans.isEmpty()) {
                return null;
            }
            List<MetricBean> filteredBeans = getFilteredBeans(beans);

            logger.debug("Found {} metric beans and after filtering list work with {} . Time = {}ms, for {}", beans.size(), filteredBeans.size(),
                    sw.stop().elapsed(TimeUnit.MILLISECONDS),
                    timestampFormatter.format(pollingWindowStart));

            sw.reset().start();
            List<Metric> metrics = client.getMetrics(filteredBeans);
            logger.debug("metrics fetched. Time: {} ms; Metrics: {}", sw.stop().elapsed(TimeUnit.MILLISECONDS), metrics.size());
            if (logger.isTraceEnabled()) printToFile(metrics);
            return changeTimeTo(pollingWindowStart, metrics);
        } catch (MBeanClient.MBeanClientPollingFailure e) {
            logger.error("Failed polling metrics from client ({}): {}", client.getClass().toString(), e.getMessage(), e);
            return null;
        }
    }

    public List<MetricBean> getFilteredBeans(List<MetricBean> beans) {
        List<MetricBean> filteredBeans = beans.stream()
                .filter(bean -> beansWhiteListPattern.matcher(bean.getName()).find())
                .collect(Collectors.toList());
        filteredBeans.removeAll(beans.stream()
                .filter((bean -> beansBlackListPattern.matcher(bean.getName()).find()))
                .collect(Collectors.toList()));
        return filteredBeans;
    }

    public void pollAndSend() {

        try {
            logger.debug("polling metrics");
            List<Metric> metrics = poll();

            if (metrics == null || metrics.isEmpty()) return;
            addPrefix(metrics);
            Stopwatch sw = Stopwatch.createStarted();
            sendToListener(metrics);
            logger.debug("metrics sent to listener. Time: {} ms",
                    sw.stop().elapsed(TimeUnit.MILLISECONDS));

        } catch (Throwable t) {
            logger.error("Unexpected error occured while polling and sending. Error = {}", t.getMessage(), t);
            // not throwing out since the scheduler will stop in any exception
        }


    }

    private Instant getPollingWindowStart() {
        long now = System.currentTimeMillis();
        long pollingIntervalMs = TimeUnit.SECONDS.toMillis(pollingIntervalSeconds);
        return Instant.ofEpochMilli(now - (now % pollingIntervalMs));
    }

    private void printToFile(List<Metric> metrics) {
        for (Metric v : metrics) {
            logger.trace(v.toString());
        }
    }

    private List<Metric> changeTimeTo(Instant newTime, List<Metric> metrics) {
        return metrics.stream()
                .map(m -> new Metric(m.getName(), m.getValue(), newTime, m.getDimensions()))
                .collect(Collectors.toList());
    }

    private void addPrefix(List<Metric> metrics) {
        metrics.forEach(metric -> metric.addDimensionsToStart(metricsPrefix));
    }

    private void sendToListener(List<Metric> metrics) {
        listenerClient.writeMetrics(metrics);
    }

}
