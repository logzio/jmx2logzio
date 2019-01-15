package io.logz.jmx2logzio.Utils;

import io.logz.jmx2logzio.Jmx2LogzioConfigurationTest;
import io.logz.jmx2logzio.MetricBean;
import io.logz.jmx2logzio.clients.JavaAgentClient;
import io.logz.jmx2logzio.configuration.Jmx2LogzioConfiguration;
import org.testng.Assert;
import org.testng.annotations.Test;

import java.util.ArrayList;
import java.util.List;

public class MetricsPipelineTest {


    Jmx2LogzioConfiguration jmx2LogzioConfiguration;

    @Test
    public void whiteListConfigurationTest() {
        jmx2LogzioConfiguration = Jmx2LogzioConfigurationTest.getWhiteListTestConfiguration();
        List<MetricBean> filteredBeans = createAndFilterBeans();  //Only metrics containing MemoryUsagePercent will be returned
        Assert.assertEquals(filteredBeans.size(), 2);
    }

    @Test
    public void blackListConfigurationTest() {
        jmx2LogzioConfiguration = Jmx2LogzioConfigurationTest.getBlackListTestConfiguration();
        List<MetricBean> filteredBeans = createAndFilterBeans();  //metrics containing Max will be filtered
        Assert.assertEquals(filteredBeans.size(), 1);
    }

    private List<MetricBean> createAndFilterBeans() {
        JavaAgentClient client = new JavaAgentClient();
        MetricsPipeline metricsPipeline = new MetricsPipeline(jmx2LogzioConfiguration, client);
        List<MetricBean> beans = new ArrayList<>();
        List<String> attr = new ArrayList<>();
        attr.add("First measure");
        attr.add(("Second measure"));
        beans.add(new MetricBean("minMemoryUsagePercent", attr));
        beans.add(new MetricBean("MaxCPUUsage", attr));
        beans.add(new MetricBean("MaxMemoryUsagePercent", attr));
        return metricsPipeline.getFilteredBeans(beans);
    }

}