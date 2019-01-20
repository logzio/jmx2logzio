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
    private static final Logger logger = LoggerFactory.getLogger(JolokiaClient.class);

    private String jolokiaFullURL;
    private int connectTimeout = (int) TimeUnit.SECONDS.toMillis(30);
    private int socketTimeout = (int) TimeUnit.SECONDS.toMillis(30);

    private ObjectMapper objectMapper;
    private Stopwatch stopwatch = Stopwatch.createUnstarted();

    public JolokiaClient(String jolokiaFullURL) {

        this.jolokiaFullURL = jolokiaFullURL;
        if (!jolokiaFullURL.endsWith("/")) {
            this.jolokiaFullURL = jolokiaFullURL +"/";
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
                throw new RuntimeException("Failed listing beans from jolokia. Response = "+httpResponse.getStatusLine());
            }

            Map<String, Object> listResponse = objectMapper.readValue(httpResponse.getEntity().getContent(), Map.class);
            Map<String, Object> domains = (Map<String, Object>) listResponse.get("value");
            if (domains == null) {
                throw new RuntimeException("Response doesn't have value attribute expected from a list response");
            }
            return extractMetricsBeans(domains);
        } catch (URISyntaxException  | IOException e) {
            throw new MBeanClientPollingFailure("Failed retrieving list of beans from Jolokia. Error = "+e.getMessage(), e);
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

            HttpResponse httpResponse = Post(jolokiaFullURL+"read?ignoreErrors=true&canonicalNaming=false")
                    .connectTimeout(connectTimeout)
                    .socketTimeout(socketTimeout)
                    .bodyString(requestBody, ContentType.APPLICATION_JSON)
                    .execute().returnResponse();

            if (httpResponse.getStatusLine().getStatusCode() != 200) {
                throw new RuntimeException("Failed reading beans from jolokia. Response = "+httpResponse.getStatusLine());
            }

            String responseBody =  IOUtils.toString(httpResponse.getEntity().getContent(), "UTF-8");

            if (logger.isTraceEnabled()) logger.trace("Jolokia getBeans response:\n{}", responseBody);

            ArrayList<Map<String, Object>> responses = objectMapper.readValue(responseBody, ArrayList.class);

            List<Metric> metrics = Lists.newArrayList();
            for (Map<String, Object> response : responses) {
                Map<String, Object> request = (Map<String, Object>) response.get("request");
                String mBeanName = (String) request.get("mbean");
                int status = (int) response.get("status");
                if (status != 200) {
                    String errMsg = "Failed reading mbean '" + mBeanName +"': "+status+" - "+response.get("error");
                    if (logger.isDebugEnabled()) {
                        logger.debug(errMsg +". Stacktrace = {}", response.get("stacktrace"));
                    } else {
                        logger.warn(errMsg);
                    }
                    continue;
                }
                Instant metricTime =  Instant.ofEpochMilli((int) response.get("timestamp"));
                String[] serviceNameAndArgs = mBeanName.split(":");
                if (serviceNameAndArgs.length != 2 ){
                    logger.debug("metric name {} not valid", mBeanName);
                    continue;
                }

                String serviceName = serviceNameAndArgs[0];
                String argsString = Metric.DOMAIN_NAME + "=" + serviceName + "," + serviceNameAndArgs[1];
                List<Dimension> dimensions = Splitter.on(',').splitToList(argsString).stream().map(this::stringArgToDimension).collect(Collectors.toList());

                Map<String, Object> attrValues = (Map<String, Object>) response.get("value");
                Map<String, Number> metricToValue = flatten(attrValues);
                for (String attrMetricName : metricToValue.keySet()) {
                    try {
                        metrics.add(new Metric(
                                attrMetricName,
                                metricToValue.get(attrMetricName),
                                metricTime,
                                dimensions
                        ));
                    } catch (IllegalArgumentException e) {
                        logger.info("Can't sent Metric since it's invalid: "+e.getMessage());
                    }
                }
            }

            return metrics;
        } catch (IOException e) {
            throw new MBeanClientPollingFailure("Failed reading beans from Jolokia. Error = "+e.getMessage(), e);
        }
    }

    private Dimension stringArgToDimension(String arg){
        String[] argKeyVale = arg.split("=");
        Dimension dimension = new Dimension(argKeyVale[0], argKeyVale.length > 1 ? argKeyVale[1] : "");
        return dimension;
    }

    private List<MetricBean> extractMetricsBeans(Map<String, Object> domains) {
        List<MetricBean> result = Lists.newArrayList();
        for (String domainName : domains.keySet()) {
            Map<String, Object> domain = (Map<String, Object>) domains.get(domainName);
            for (String mbeanName : domain.keySet()) {
                Map<String, Object> mbean = (Map<String, Object>) domain.get(mbeanName);
                Map<String, Object> attributes = (Map<String, Object>) mbean.get("attr");

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
            } else {
                if (value instanceof Number) {
                    metricValues.put(sanitizeMetricName(key, /*keepDot*/ false), (Number) value);
                }
            }
        }
        return metricValues;
    }

}
