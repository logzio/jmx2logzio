package io.logz.jmx2logzio.objects;

import io.logz.jmx2logzio.MetricBean;

import java.util.List;

/**
 * Created by Yogev Mets on 3/1/18.
        */
public abstract class MBeanClient {

    public abstract  List<MetricBean> getBeans();
    public abstract List<Metric> getMetrics(List<MetricBean> beans);

    public static class MBeanClientPollingFailure extends RuntimeException {

        public MBeanClientPollingFailure(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
