package io.logz.jmx2logzio.clients;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.PropertyAccessor;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.base.Joiner;
import com.google.common.base.Splitter;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import io.logz.jmx2logzio.MetricBean;
import io.logz.jmx2logzio.Utils.Predicator;
import io.logz.jmx2logzio.objects.Dimension;
import io.logz.jmx2logzio.objects.MBeanClient;
import io.logz.jmx2logzio.objects.Metric;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.management.*;
import javax.management.openmbean.CompositeData;
import javax.management.openmbean.TabularData;
import java.lang.management.ManagementFactory;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

import static io.logz.jmx2logzio.Utils.MetricsUtils.sanitizeMetricName;

public class JavaAgentClient extends MBeanClient {

    private final Logger logger = LoggerFactory.getLogger(JavaAgentClient.class);
    private static final int INSTANCES_NOT_FOUND_PERCENTAGE_WARNING_THRESHOLD = 10;
    private static final int DIMENSION_INDEX = 1;
    private static final int DOMAIN_NAME_INDEX = 0;
    private static final int ARGUMENT_KEY_INDEX = 0;
    private static final int ARGUMENT_VALUE_INDEX = 1;

    private final MBeanServer server;
    private final ObjectMapper objectMapper;
    private List<Dimension> extraDimensions;

    public JavaAgentClient() {
        server = ManagementFactory.getPlatformMBeanServer();
        // The visibility section here is to tell Jackson that we want it to get over all the object properties and not only the getters
        // If we wont set it, then it will fetch only partial info from the MBean objects
        objectMapper = new ObjectMapper();
        objectMapper.setVisibility(PropertyAccessor.ALL, JsonAutoDetect.Visibility.NONE);
        objectMapper.setVisibility(PropertyAccessor.FIELD, JsonAutoDetect.Visibility.ANY);
        extraDimensions = new ArrayList<>();
    }

    /**
     * Override a MBeanClient's method, get Metric Beans from the MBean Server
     * @return a List of Metric Beans (both from JVM and the app)
     * @throws MBeanClientPollingFailure
     */
    @Override
    public List<MetricBean> getBeans() {
        int instancesCount = 0;
        int instanceNotFoundCount = 0;
        List<MetricBean> metricBeans = Lists.newArrayList();
        Set<ObjectInstance> instances = server.queryMBeans(null, null);

        for (ObjectInstance instance : instances) {
            instancesCount++;
            try {
                MBeanInfo mBeanInfo = server.getMBeanInfo(instance.getObjectName());
                List<String> attributes = Lists.newArrayList();

                for (MBeanAttributeInfo attribute : mBeanInfo.getAttributes()) {
                    attributes.add(attribute.getName());
                }

                // Dont change to getCanonicalName(), we need it to preserve the order so we can have a valuable metrics tree
                metricBeans.add(new MetricBean(instance.getObjectName().getDomain() + ":" + instance.getObjectName().getKeyPropertyListString(), attributes));
            } catch (InstanceNotFoundException e) {
                logger.debug("Instance Not found: {}", e.getMessage(), e);
                instanceNotFoundCount++;
            }  catch (IntrospectionException e) {
                logger.warn("Error inspecting MBean: {}", e.getMessage(), e);
            } catch (ReflectionException e) {
                logger.warn("An error occurred at MBean server while trying to invoke methods on MBeans :{}", e.getMessage(), e);
            }
        }
        if (((double) instanceNotFoundCount / instancesCount) * 100 > INSTANCES_NOT_FOUND_PERCENTAGE_WARNING_THRESHOLD) {
            logger.warn("more than {}% of instances were not found! ({} out of {})", INSTANCES_NOT_FOUND_PERCENTAGE_WARNING_THRESHOLD, instanceNotFoundCount, instancesCount);
        }

        return metricBeans;
    }

    /**
     * Converts Metric Beans to Metrics (logz.io)
     * @param beans a list of MetricBeans
     * @return a list of Metrics, after extracting and adding dimensions to each metric
     * @throws MBeanClientPollingFailure if metric polling failed
     */
    @Override
    public List<Metric> getMetrics(List<MetricBean> beans) throws MBeanClientPollingFailure {
        List<Metric> metrics = Lists.newArrayList();
        for (MetricBean metricBean : beans) {
            List<Dimension> dimensions = getDimensions(metricBean);
            if (dimensions != null) {
                Metric metric = getMetricsForBean(metricBean, dimensions);
                if (metric.getMetricMap() != null) {
                    metrics.add(metric);
                }
            }
        }
        return metrics;
    }

    /**
     * Flattens "metrics tree" and converts it to a list of metrics
     * @param metricBean a single metric bean
     * @param dimensions a list of dimensions for the specific metric
     * @return a list of logz.io metrics
     */
    private Metric getMetricsForBean(MetricBean metricBean, List<Dimension> dimensions) {
        Instant metricTime = Instant.now();
        Metric metric = new Metric();
        try {
            AttributeList attributeList = server.getAttributes(new ObjectName(metricBean.getName()),
                    metricBean.getAttributes().toArray(new String[0]));

            Map<String, Object> attrValues = new HashMap<>(attributeList.size());
            attributeList.asList().forEach((attr) ->
                    attrValues.put(attr.getName(), attr.getValue()));

            Map<String, Number> metricToValue = flatten(attrValues);
            if (!metricToValue.isEmpty()) {
                try {
                    metric = new Metric(metricToValue, metricTime, dimensions);
                } catch (IllegalArgumentException e) {
                    logger.warn("Failed converting metric name to Logz.io-friendly name: metricsBean.getName = {}", metricBean.getName(), e);
                }
            }
        } catch (MalformedObjectNameException | ReflectionException | InstanceNotFoundException | IllegalArgumentException e) {
            throw new MBeanClientPollingFailure("Failed to poll Mbean " + e.getMessage(), e);
        }
        return metric;
    }

    /**
     * Collect dimensions from a metric bean and add custom dimensions (from the configurations)
     * @param metricBean a single metric bean
     * @return a list of dimensions for that metric
     */
    private List<Dimension> getDimensions(MetricBean metricBean) {
        String[] domainNameAndOtherDimensions = metricBean.getName().split(":");

        if (domainNameAndOtherDimensions.length < 2) {
            logger.error("metric full path: {} doesn't have domain name and dimensions", metricBean.getName());
            return null;
        }

        List<Dimension> dimensions = Splitter.on(',')
                .splitToList(domainNameAndOtherDimensions[DIMENSION_INDEX])
                .stream()
                .map(this::stringArgToDimension)
                .collect(Collectors.toList());
        dimensions.add(0, new Dimension("domainName", domainNameAndOtherDimensions[DOMAIN_NAME_INDEX]));
        dimensions.addAll(extraDimensions);
        return dimensions;
    }

    private Dimension stringArgToDimension(String arg) {
        String[] argKeyVale = arg.split("=");
        Dimension dimension = new Dimension(sanitizeMetricName(argKeyVale[ARGUMENT_KEY_INDEX], false), argKeyVale.length > 1 ? sanitizeMetricName(argKeyVale[ARGUMENT_VALUE_INDEX], false) : "");
        return dimension;
    }

    private Map<String, Number> flatten(Map<String, Object> attrValues) {

        final Map<String, Number> metricValues = Maps.newHashMap();
        for (final String key : attrValues.keySet()) {
            final Object value = attrValues.get(key);
            if (value == null || value.getClass().isArray()) {
                continue;
            }
            Map<Predicator, Runnable> addValuesByType = new HashMap<>();
            addValuesByType.put((obj) -> obj instanceof Number, () -> metricValues.put(sanitizeMetricName(key, /*keepDot*/ true), (Number) value));
            addValuesByType.put((obj) -> obj instanceof CompositeData, () -> {
                CompositeData data = (CompositeData) value;
                Map<String, Object> valueMap = handleCompositeData(data);
                metricValues.putAll(prependKey(key, flatten(valueMap)));
            });
            addValuesByType.put((obj) -> obj instanceof TabularData, () -> {
                TabularData tabularData = (TabularData) value;
                Map<String, Object> rowKeyToRowData = handleTabularData(tabularData);
                metricValues.putAll(prependKey(key, flatten(rowKeyToRowData)));
            });
            addValuesByType.put((obj) -> obj instanceof String, () -> {});
            addValuesByType.put((obj) -> obj instanceof Boolean, () -> {});

            Optional<Map.Entry<Predicator, Runnable>> resultEntry = addValuesByType.entrySet().stream().filter(entry -> entry.getKey().validatePredicate(value)).findFirst();
            if (!resultEntry.equals(Optional.empty())) {
                resultEntry.get().getValue().run();
            } else {
                Map<String, Object> valueMap;
                try {
                    valueMap = objectMapper.convertValue(value, new TypeReference<Map<String, Object>>() {});
                    metricValues.putAll(prependKey(key, flatten(valueMap)));
                } catch (Exception e) {
                    logger.trace("Can't convert attribute named {} with class type {}", key, value.getClass().getCanonicalName(), e);
                }
            }

        }
        return metricValues;
    }

    private Map<String, Object> handleTabularData(TabularData tabularData) {
        Map<String, Object> rowKeyToRowData = new HashMap<>();

        tabularData.keySet().forEach(rowKey -> {
            List<?> rowKeyAsList = (List<?>) rowKey;
            rowKeyToRowData.put(createKey(rowKeyAsList), tabularData.get(rowKeyAsList.toArray()));
        });
        return rowKeyToRowData;
    }

    private Map<String, Object> handleCompositeData(CompositeData data) {
        Map<String, Object> valueMap = new HashMap<>();

        data.getCompositeType().keySet().forEach(compositeKey -> {
            valueMap.put(compositeKey, data.get(compositeKey));
        });
        return valueMap;
    }

    private String createKey(List<?> rowKeyAsList) {
        return Joiner.on('_').join(rowKeyAsList);
    }

    private Map<String, Number> prependKey(String key, Map<String, Number> keyToNumber) {
        Map<String, Number> result = new HashMap<>();
        for (String internalMetricName : keyToNumber.keySet()) {
            String resultKey = key.equalsIgnoreCase("value") ? sanitizeMetricName(internalMetricName, /*keepDot*/ true) :
                    sanitizeMetricName(key, /*keepDot*/ false) + "." + sanitizeMetricName(internalMetricName, /*keepDot*/ true);
            result.put(resultKey, keyToNumber.get(internalMetricName));
        }
        return result;
    }

    @Override
    public void setExtraDimensions(List<Dimension> extraDimensions) {
        this.extraDimensions = extraDimensions;
    }
}
