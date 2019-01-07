package io.logz.jmx2logzio.objects;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.PropertyAccessor;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.base.Joiner;
import com.google.common.base.Splitter;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import io.logz.jmx2logzio.MetricBean;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.management.*;
import javax.management.openmbean.CompositeData;
import javax.management.openmbean.TabularData;
import java.lang.management.ManagementFactory;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static io.logz.jmx2logzio.Utils.MetricsUtils.sanitizeMetricName;

/**
 * Created by roiravhon on 6/6/16.
 */
public class JavaAgentClient extends MBeanClient {

    private static final Logger logger = LoggerFactory.getLogger(JavaAgentClient.class);

    private final MBeanServer server;
    private final ObjectMapper objectMapper;

    public JavaAgentClient() {

        server = ManagementFactory.getPlatformMBeanServer();

        // The visibility section here is to tell Jackson that we want it to get over all the object properties and not only the getters
        // If we wont set it, then it will fetch only partial info from the MBean objects
        objectMapper = new ObjectMapper();
        objectMapper.setVisibility(PropertyAccessor.ALL, JsonAutoDetect.Visibility.NONE);
        objectMapper.setVisibility(PropertyAccessor.FIELD, JsonAutoDetect.Visibility.ANY);
    }

    @Override
    public List<MetricBean> getBeans() throws MBeanClientPollingFailure {

        try {
            List<MetricBean> metricBeans = Lists.newArrayList();
            Set<ObjectInstance> instances = server.queryMBeans(null, null);

            for (ObjectInstance instance : instances) {
                MBeanInfo mBeanInfo = server.getMBeanInfo(instance.getObjectName());
                List<String> attributes = Lists.newArrayList();

                for (MBeanAttributeInfo attribute : mBeanInfo.getAttributes()) {
                    attributes.add(attribute.getName());
                }

                // Dont change to getCanonicalName(), we need it to preserve the order so we can have a valuable metrics tree
                metricBeans.add(new MetricBean(instance.getObjectName().getDomain() + ":" + instance.getObjectName().getKeyPropertyListString(), attributes));
            }
            return metricBeans;

        } catch (IntrospectionException | ReflectionException | InstanceNotFoundException e) {
            throw new MBeanClientPollingFailure(e.getMessage(), e);
        }
    }

    @Override
    public List<Metric> getMetrics(List<MetricBean> beans) throws MBeanClientPollingFailure{

        List<Metric> metrics = Lists.newArrayList();
        Instant metricTime = Instant.now();

        for (MetricBean metricBean : beans) {
            try {
                AttributeList attributeList = server.getAttributes(new ObjectName(metricBean.getName()),
                                                                   metricBean.getAttributes().toArray(new String[0]));

                Map<String, Object> attrValues = new HashMap<>(attributeList.size());
                attributeList.asList().forEach((attr) ->
                        attrValues.put(attr.getName(), attr.getValue()));

                Map<String, Number> metricToValue = flatten(attrValues);

                String[] domainNameAndOtherDimensions = metricBean.getName().split(":");

                if (domainNameAndOtherDimensions.length < 2) {
                    logger.error("metric full path: {} doesn't have domain name and dimensions", metricBean.getName());
                    continue;
                }

                List<Dimension> dimensions = Splitter.on(',')
                        .splitToList(domainNameAndOtherDimensions[1])
                        .stream()
                        .map(this::stringArgToDimension)
                        .collect(Collectors.toList());
                dimensions.add(0, new Dimension("domainName", domainNameAndOtherDimensions[0]));

                for (String attrMetricName : metricToValue.keySet()) {
                    try {
                        metrics.add(new Metric(
                                attrMetricName,
                                metricToValue.get(attrMetricName),
                                metricTime,
                                dimensions
                        ));
                    } catch (IllegalArgumentException e) {
                        logger.info("Failed converting metric name to Logz.io-friendly name: metricsBean.getName = {}, attrMetricName = {}", metricBean.getName(), attrMetricName, e);
                    }
                }
            } catch (MalformedObjectNameException | ReflectionException | InstanceNotFoundException | IllegalArgumentException e ) {
                throw new MBeanClientPollingFailure(e.getMessage(), e);
            }
        }

        return metrics;
    }

    private Dimension stringArgToDimension(String arg){
        String[] argKeyVale = arg.split("=");
        Dimension dimension = new Dimension(sanitizeMetricName(argKeyVale[0], false), argKeyVale.length > 1 ? sanitizeMetricName(argKeyVale[1], false) : "");
        return dimension;
    }

    private Map<String, Number> flatten(Map<String, Object> attrValues) {

        Map<String, Number> metricValues = Maps.newHashMap();
        for (String key : attrValues.keySet()) {
            Object value = attrValues.get(key);
            if (value == null) {
                continue;
            }
            if (value.getClass().isArray()) continue;
            else if (value instanceof Number) {
                metricValues.put(sanitizeMetricName(key, /*keepDot*/ false), (Number) value);
            } else if (value instanceof CompositeData) {
                CompositeData data = (CompositeData) value;
                Map<String, Object> valueMap = handleCompositeData(data);
                metricValues.putAll(prependKey(key, flatten(valueMap)));
            } else if (value instanceof TabularData) {
                TabularData tabularData = (TabularData) value;
                Map<String, Object> rowKeyToRowData = handleTabularData(tabularData);
                metricValues.putAll(prependKey(key, flatten(rowKeyToRowData)));
            } else if (!(value instanceof String) && !(value instanceof Boolean)) {
                Map<String, Object> valueMap;
                try {
                    valueMap = objectMapper.convertValue(value, new TypeReference<Map<String, Object>>() {});
                } catch (Exception e) {
                    logger.trace("Can't convert attribute named {} with class type {}", key, value.getClass().getCanonicalName());
                    continue;
                }
                metricValues.putAll(prependKey(key, flatten(valueMap)));
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
            String resultKey;
            if (key.equalsIgnoreCase("value")) {
                resultKey =  sanitizeMetricName(internalMetricName, /*keepDot*/ false);
            } else {
                resultKey = sanitizeMetricName(key, /*keepDot*/ false) + "."
                        + sanitizeMetricName(internalMetricName, /*keepDot*/ false);
            }
            result.put(resultKey, keyToNumber.get(internalMetricName));
        }
        return result;
    }

}
