package io.logz.jmx2logzio.Utils;

import org.slf4j.Logger;
import io.logz.jmx2logzio.Jmx2LogzioConfigurationTest;
import io.logz.jmx2logzio.MetricBean;
import io.logz.jmx2logzio.clients.JavaAgentClient;
import io.logz.jmx2logzio.configuration.Jmx2LogzioConfiguration;
import org.apache.commons.io.FileUtils;
import org.slf4j.LoggerFactory;
import org.testng.Assert;
import org.testng.annotations.AfterTest;
import org.testng.annotations.Test;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class MetricsPipelineTest {


    private Jmx2LogzioConfiguration jmx2LogzioConfiguration;
    private final Logger logger = LoggerFactory.getLogger(MetricsPipeline.class);

    @AfterTest
    private void clean() {
        try {
            logger.info("removing test metrics directory");
            FileUtils.deleteDirectory(new File(Jmx2LogzioConfigurationTest.METRICS_TEST_DIR));
        } catch (IOException e) {
            logger.error("couldn't remove temp metrics directory {}: {}", Jmx2LogzioConfigurationTest.METRICS_TEST_DIR, e.getMessage(), e);
        }
    }

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