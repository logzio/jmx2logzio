package io.logz.jmx2logzio.Utils;

import com.google.common.base.Splitter;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import io.logz.jmx2logzio.Jmx2LogzioConfigurationTest;
import io.logz.jmx2logzio.clients.JavaAgentClient;
import io.logz.jmx2logzio.configuration.Jmx2LogzioConfiguration;
import io.logz.jmx2logzio.exceptions.IllegalConfiguration;
import org.testng.annotations.BeforeTest;
import org.testng.annotations.Test;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Scanner;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import static java.lang.Thread.sleep;
import static org.testng.Assert.*;

public class MetricsPipelineTest {


    Config config;
    Jmx2LogzioConfiguration jmx2LogzioConfiguration;

    @BeforeTest
    private void setup(){
        jmx2LogzioConfiguration = Jmx2LogzioConfigurationTest.getTestConfiguration();
    }

    @Test
    public void testPollAndSend() {
        JavaAgentClient client = new JavaAgentClient();
        MetricsPipeline metricsPipeline = new MetricsPipeline(jmx2LogzioConfiguration,client);
        metricsPipeline.pollAndSend();

        try {
            sleep(60000);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        System.out.println("End. ");
    }
}