package io.logz.jmx2logzio.Utils;

public class MetricsUtils {
    public static String sanitizeMetricName(String s, boolean keepDot) {
        StringBuilder sb = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '=') {
                sb.append('_');
            } else if (c == ':') {
                sb.append('.');
            } else if (c == ',') {
                sb.append('.');
            } else if (c == '.' && !keepDot) {
                sb.append('_');
            } else if (c == '"') {
                // Removing it
            } else if (c == ' ') {
                sb.append('-');
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    public static String sanitizeMetricName(String s) {
        return MetricsUtils.sanitizeMetricName(s, true);
    }
}
