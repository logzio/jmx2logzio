package io.logz.jmx2logzio.clients;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.google.common.base.Splitter;
import com.google.common.base.Stopwatch;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import io.logz.jmx2logzio.MetricBean;
import io.logz.jmx2logzio.objects.Dimension;
import io.logz.jmx2logzio.objects.JolokiaReadRequest;
import io.logz.jmx2logzio.objects.MBeanClient;
import io.logz.jmx2logzio.objects.Metric;
import org.apache.commons.io.IOUtils;
import org.apache.http.HttpResponse;
import org.apache.http.entity.ContentType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URISyntaxException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static io.logz.jmx2logzio.Utils.MetricsUtils.sanitizeMetricName;
import static org.apache.http.client.fluent.Request.Get;
import static org.apache.http.client.fluent.Request.Post;

public class JolokiaClient extends MBeanClient {

    public static final String POLLER_JOLOKIA = "service.poller.jolokia";
    public static final String JOLOKIA_FULL_URL = "service.poller.jolokia.jolokiaFullUrl";

    public static final String LISTENER_URL = "LISTENER_URL";
    public static final String WHITE_LIST_REGEX = "WHITE_LIST_REGEX";
    public static final String BLACK_LIST_REGEX = "BLACK_LIST_REGEX";
    public static final String LOGZIO_TOKEN = "LOGZIO_TOKEN";
    public static final String SERVICE_NAME = "SERVICE_NAME";
    public static final String SERVICE_HOST = "SERVICE_HOST";
    public static final String POLLING_INTERVAL_IN_SEC = "POLLING_INTERVAL_IN_SEC";
    public static final String FROM_DISK = "FROM_DISK";
    public static final String IN_MEMORY_QUEUE_CAPACITY = "IN_MEMORY_QUEUE_CAPACITY";
    public static final String LOGS_COUNT_LIMIT = "LOGS_COUNT_LIMIT";
    public static final String DISK_SPACE_CHECKS_INTERVAL = "DISK_SPACE_CHECKS_INTERVAL";
    public static final String QUEUE_DIR = "QUEUE_DIR";
    public static final String FILE_SYSTEM_SPACE_LIMIT = "FILE_SYSTEM_SPACE_LIMIT";
    public static final String CLEAN_SENT_METRICS_INTERVAL = "CLEAN_SENT_METRICS_INTERVAL";
    private static final Logger logger = LoggerFactory.getLogger(JolokiaClient.class);

    private static final String REQUEST_MBEAN_KEY = "mbean";
    private static final String RESPONSE_REQUEST_KEY = "request";
    private static final String RESPONSE_STATUS_KEY = "status";
    private static final String RESPONSE_ERROR_KEY = "error";
    private static final String RESPONSE_STACKTRACE_KEY = "stacktrace";
    private static final String RESPONSE_TIMESTAMP_KEY = "timestamp";
    private static final String RESPONSE_VALUE_KEY = "value";
    private static final String MBEAN_ATTR_KEY = "attr";
    private static final int SERVICE_NAME_INDEX = 0;
    private static final int ARGUMENTS_INDEX = 1;
    private static final int ARGUMENT_KEY_INDEX = 0;
    private static final int ARGUMENT_VALUE_INDEX = 1;

    private String jolokiaFullURL;
    private final int connectTimeout = (int) TimeUnit.SECONDS.toMillis(30);
    private final int socketTimeout = (int) TimeUnit.SECONDS.toMillis(30);

    private final ObjectMapper objectMapper;
    private final Stopwatch stopwatch = Stopwatch.createUnstarted();

    public JolokiaClient(String jolokiaFullURL) {

        this.jolokiaFullURL = jolokiaFullURL;
        if (!jolokiaFullURL.endsWith("/")) {
            this.jolokiaFullURL = jolokiaFullURL + "/";
        }
        objectMapper = new ObjectMapper();
        objectMapper.enable(SerializationFeature.INDENT_OUTPUT);
    }

    public List<MetricBean> getBeans() throws MBeanClientPollingFailure {
        try {
            stopwatch.reset().start();
            logger.debug("Retrieving /list of bean from Jolokia ({})...", jolokiaFullURL);
            HttpResponse httpResponse = Get(new URI(jolokiaFullURL + "list?canonicalNaming=false"))
                    .connectTimeout(connectTimeout)
                    .socketTimeout(socketTimeout)
                    .execute().returnResponse();
            logger.debug("GET /list from jolokia took {} ms", stopwatch.stop().elapsed(TimeUnit.DAYS.MILLISECONDS));
            if (httpResponse.getStatusLine().getStatusCode() != 200) {
                throw new RuntimeException("Failed listing beans from jolokia. Response = " + httpResponse.getStatusLine());
            }

            Map<String, Object> listResponse = objectMapper.readValue(httpResponse.getEntity().getContent(), Map.class);
            Map<String, Object> domains = (Map<String, Object>) listResponse.get(RESPONSE_VALUE_KEY);
            if (domains == null) {
                throw new RuntimeException("Response doesn't have value attribute expected from a list response");
            }
            return extractMetricsBeans(domains);
        } catch (URISyntaxException | IOException e) {
            throw new MBeanClientPollingFailure("Failed retrieving list of beans from Jolokia. Error = " + e.getMessage(), e);
        }
    }

    public List<Metric> getMetrics(List<MetricBean> beans) throws MBeanClientPollingFailure {
        List<JolokiaReadRequest> readRequests = Lists.newArrayList();
        for (MetricBean bean : beans) {
            readRequests.add(new JolokiaReadRequest(bean.getName(), bean.getAttributes()));
        }

        try {
            String requestBody = objectMapper.writeValueAsString(readRequests);
            if (logger.isTraceEnabled()) logger.trace("Jolokia getBeans request body: {}", requestBody);
            HttpResponse httpResponse = sendToJolokia(jolokiaFullURL, requestBody);
            String responseBody = IOUtils.toString(httpResponse.getEntity().getContent(), "UTF-8");

            if (logger.isTraceEnabled()) {
                logger.trace("Jolokia getBeans response:\n{}", responseBody);
            }
            ArrayList<Map<String, Object>> responses = objectMapper.readValue(responseBody, ArrayList.class);

            List<Metric> metrics = Lists.newArrayList();
            for (Map<String, Object> response : responses) {
                metrics.addAll(getMetricsForResponse(response));
            }
            return metrics;
        } catch (IOException e) {
            throw new MBeanClientPollingFailure("Failed reading beans from Jolokia. Error = " + e.getMessage(), e);
        }
    }

    private List<Metric> getMetricsForResponse(Map<String, Object> response) {
        Map<String, Object> request = (Map<String, Object>) response.get(RESPONSE_REQUEST_KEY);
        String mBeanName = (String) request.get(REQUEST_MBEAN_KEY);
        int status = (int) response.get(RESPONSE_STATUS_KEY);
        if (status != HttpURLConnection.HTTP_OK) {
            logger.warn("Failed reading mbean '" + mBeanName + "': " + status + " - " + response.get(RESPONSE_ERROR_KEY) +
                    ". Stacktrace = {}", response.get(RESPONSE_STACKTRACE_KEY));
            return new ArrayList<>();
        }
        Instant metricTime = Instant.ofEpochMilli((int) response.get(RESPONSE_TIMESTAMP_KEY));
        String[] serviceNameAndArgs = mBeanName.split(":");
        if (serviceNameAndArgs.length != 2) {
            logger.debug("metric name {} not valid", mBeanName);
            return new ArrayList<>();
        }

        String serviceName = serviceNameAndArgs[SERVICE_NAME_INDEX];
        String argsString = Metric.DOMAIN_NAME + "=" + serviceName + "," + serviceNameAndArgs[ARGUMENTS_INDEX];
        List<Dimension> dimensions = Splitter.on(',').splitToList(argsString).stream().map(this::stringArgToDimension).collect(Collectors.toList());

        Map<String, Object> attrValues = (Map<String, Object>) response.get(RESPONSE_VALUE_KEY);
        Map<String, Number> metricToValue = flatten(attrValues);
        List<Metric> metrics = new ArrayList<>();
        for (String attrMetricName : metricToValue.keySet()) {
            try {
                metrics.add(new Metric(
                        attrMetricName,
                        metricToValue.get(attrMetricName),
                        metricTime,
                        dimensions
                ));
            } catch (IllegalArgumentException e) {
                logger.error("Can't send Metric since it's invalid: " + e.getMessage());
            }
        }
        return metrics;
    }

    private HttpResponse sendToJolokia(String jolokiaFullURL, String requestBody) {
        HttpResponse httpResponse;
        try {
            httpResponse = Post(jolokiaFullURL + "read?ignoreErrors=true&canonicalNaming=false")
                    .connectTimeout(connectTimeout)
                    .socketTimeout(socketTimeout)
                    .bodyString(requestBody, ContentType.APPLICATION_JSON)
                    .execute().returnResponse();
        } catch (IOException e) {
            throw new MBeanClientPollingFailure("Failed reading beans from Jolokia. Error = " + e.getMessage(), e);
        }

        if (httpResponse.getStatusLine().getStatusCode() != HttpURLConnection.HTTP_OK) {
            throw new RuntimeException("Failed reading beans from jolokia. Response = " + httpResponse.getStatusLine());
        }
        return httpResponse;
    }

    private Dimension stringArgToDimension(String arg) {
        String[] argKeyVale = arg.split("=");
        Dimension dimension = new Dimension(argKeyVale[ARGUMENT_KEY_INDEX], argKeyVale.length > 1 ? argKeyVale[ARGUMENT_VALUE_INDEX] : "");
        return dimension;
    }

    private List<MetricBean> extractMetricsBeans(Map<String, Object> domains) {
        List<MetricBean> result = Lists.newArrayList();
        for (String domainName : domains.keySet()) {
            Map<String, Object> domain = (Map<String, Object>) domains.get(domainName);
            for (String mbeanName : domain.keySet()) {
                Map<String, Object> mbean = (Map<String, Object>) domain.get(mbeanName);
                Map<String, Object> attributes = (Map<String, Object>) mbean.get(MBEAN_ATTR_KEY);

                if (attributes != null) {
                    List<String> attrNames = new ArrayList<String>(attributes.keySet());
                    result.add(new MetricBean(domainName + ":" + mbeanName, attrNames));
                }
            }
        }
        return result;
    }

    private static Map<String, Number> flatten(Map<String, Object> attrValues) {
        Map<String, Number> metricValues = Maps.newHashMap();
        for (String key : attrValues.keySet()) {
            Object value = attrValues.get(key);
            if (value instanceof Map) {
                Map<String, Number> flattenValueTree = flatten((Map) value);

                for (String internalMetricName : flattenValueTree.keySet()) {
                    metricValues.put(
                            sanitizeMetricName(key, /*keepDot*/ false) + "."
                                    + sanitizeMetricName(internalMetricName, /*keepDot*/ false),
                            flattenValueTree.get(internalMetricName));
                }
            } else if (value instanceof Number) {
                metricValues.put(sanitizeMetricName(key, /*keepDot*/ false), (Number) value);
            }
        }
        return metricValues;
    }

}
