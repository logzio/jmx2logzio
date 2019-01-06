package io.logz.jmx2logzio;

import io.logz.jmx2logzio.configuration.Jmx2LogzioConfiguration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;

public class Jmx2LogzioJolokia {
    private static final Logger logger = LoggerFactory.getLogger(Jmx2LogzioJolokia.class);

    public static void main(String[] args) {
        Config config = ConfigFactory.load();
        Jmx2LogzioConfiguration jmx2LogzioConfiguration = new Jmx2LogzioConfiguration(config);

    }
}
