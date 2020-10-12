package io.logz.jmx2logzio.objects;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class Metric {
    public static DateTimeFormatter timestampFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSZ").withZone(ZoneId.of("UTC"));
    public static final String SERVICE_NAME = "serviceName";
    public static final String SERVICE_HOST = "serviceHost";
    public static final String DOMAIN_NAME = "domainName";
    private Instant timestamp;

    @JsonProperty("dim")
    private Map<String, Object> dimensionsMap;

    @JsonProperty("metrics")
    private Map<String, Number> metricMap;

    @JsonIgnore

    private List<Dimension> dimensions;

    public Metric() {
    }

    public Metric(Map<String, Number> metricMap, Instant timestamp, List<Dimension> dimensions) {
        this.timestamp = timestamp;
        this.dimensions = new ArrayList<>(dimensions);
        this.dimensionsMap = getDimensionsMap();
        this.metricMap = metricMap;
    }


    @JsonProperty("@timestamp")
    public String getFormattedTimestamp() {
        return timestampFormatter.format(timestamp);
    }

    @JsonIgnore
    public Instant getTimestamp() {
        return timestamp;
    }

    @JsonIgnore
    public List<Dimension> getDimensions() {
        return dimensions;
    }

    @Override
    public String toString() {
        return "Metric{" +
                ", map=" + metricMap.toString() +
                ", timestamp=" + timestamp +
                ", dimensions=" + dimensions.toString() +
                '}';
    }

    /**
     * Converts the dimensions property to map for jsonify
     * @return dimensions as map
     */
    public Map<String, Object> getDimensionsMap() {
        Map<String, Object> jsonDimensions = new HashMap<>();
        for (Dimension dimension : dimensions) {
            jsonDimensions.put(dimension.getKey(),dimension.getValue());
        }
        return jsonDimensions;
    }

    public Map<String, Number> getMetricMap() {
        return metricMap;
    }

    public void addDimensionsToStart(List<Dimension> dimensionToAdd) {
        this.dimensions.addAll(0, dimensionToAdd);
    }
}
