package io.logz.jmx2logzio.objects;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class Metric {
    public static DateTimeFormatter timestampFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSZ").withZone(ZoneId.of("UTC"));
    public static final String SERVICE_NAME = "serviceName";
    public static final String SERVICE_HOST = "serviceHost";
    public static final String DOMAIN_NAME = "domainName";
    private String timestamp;

    @JsonProperty("dim")
    private Map<String, Object> dimensionsMap;

    @JsonProperty("metrics")
    private Map<String, Number> metricMap;

    @JsonIgnore
    private String name;
    private Number value;
    private List<Dimension> dimensions;

    public Metric() {
    }

    public Metric(String name, Number value, Instant timestamp, List<Dimension> dimensions) {

        this.name = name;
        this.value = value;
        this.timestamp = timestampFormatter.format(timestamp);
        this.dimensions = dimensions.stream().collect(Collectors.toList());
        this.dimensionsMap = getDimensionsMap();
        this.metricMap = getMetricMap();
    }

    public String getName() {
        return name;
    }

    @JsonIgnore
    public Number getValue() {
        return value;
    }

    @JsonProperty("@timestamp")
    public String getTimestamp() {
        return timestamp;
    }

    @JsonIgnore
    public List<Dimension> getDimensions() {
        return dimensions;
    }

    @Override
    public String toString() {
        return "Metric{" +
                ",name='" + name + '\'' +
                ", value=" + value +
                ", timestamp=" + timestamp +
                ", dimensions=" + dimensions.toString() +
                '}';
    }


    public Map<String, Object> getDimensionsMap() {
        Map<String, Object> jsonDimensions = new HashMap<>();
        for (Dimension dimension : dimensions) {
            jsonDimensions.put(dimension.getKey(),dimension.getValue());
        }
        return jsonDimensions;
    }

    public Map<String, Number> getMetricMap() {
        Map<String, Number> metric = new HashMap<>();
        metric.put(this.name,this.value);
        return metric;
    }

    public void addDimensionsToStart(List<Dimension> dimensionToAdd) {
        this.dimensions.addAll(0, dimensionToAdd);
    }
}
