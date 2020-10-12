package io.logz.jmx2logzio.listener;

import io.logz.jmx2logzio.Utils.Shutdownable;
import io.logz.jmx2logzio.objects.Metric;
import java.util.List;


public interface ListenerWriter extends Shutdownable {
    void writeMetrics(List<Metric> metrics);
    void start();
}
