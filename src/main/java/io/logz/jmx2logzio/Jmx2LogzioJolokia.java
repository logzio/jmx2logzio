package io.logz.jmx2logzio;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import io.logz.jmx2logzio.configuration.Jmx2LogzioConfiguration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;

public class Jmx2LogzioJolokia {
    private static final Logger logger = LoggerFactory.getLogger(Jmx2LogzioJolokia.class);

    public static final String WHITE_LIST_REGEX = "service.poller.white-list-regex";
    public static final String BLACK_LIST_REGEX = "service.poller.black-list-regex";
    public static final String SERVICE_NAME = "service.name";
    public static final String SERVICE_HOST = "service.host";
    public static final String METRICS_POLLING_INTERVAL = "service.poller.metrics-polling-interval-in-seconds";
    public static final String EXTRA_DIMENSIONS = "extra-dimensions";
    public static final String LISTENER_URL = "logzio-java-sender.url";
    public static final String LOGZIO_TOKEN = "logzio-java-sender.token";
    public static final String FROM_DISK = "logzio-java-sender.from-disk";
    public static final String IN_MEMORY_QUEUE_CAPACITY = "logzio-java-sender.in-memory-queue-capacity";
    public static final String LOGS_COUNT_LIMIT = "logzio-java-sender.log-count-limit";
    public static final String DISK_SPACE_CHECK_INTERVAL = "logzio-java-sender.disk-space-checks-interval";
    public static final String QUEUE_DIR = "logzio-java-sender.queue-dir";
    public static final String FILE_SYSTEM_SPACE_LIMIT = "logzio-java-sender.file-system-full-percent-threshold";
    public static final String CLEAN_SENT_METRICS_INTERVAL = "logzio-java-sender.clean-sent-metrics-interval";
    private static final int CONFIG_FILE_INDEX = 0;

    public static void main(String[] args) {
        logger.debug("Starting Jmx2Logzio");

        Config config;
        if (args.length > 0) {
            String configFilePath = args[CONFIG_FILE_INDEX];
           if ((new File(configFilePath)).exists()) {
               logger.info("Loading from config file: {}", configFilePath);
               config = ConfigFactory.parseFile(new File(configFilePath));
           } else {
               logger.error("config filename {} supplied but couldn't be found.", configFilePath);
               return;
           }
        } else {
            config = ConfigFactory.load();
        }

        Jmx2LogzioConfiguration jmx2LogzioConfiguration = new Jmx2LogzioConfiguration(config);
        Jmx2Logzio main = new Jmx2Logzio(jmx2LogzioConfiguration);
        logger.info("Starting jmx2Logzio using Jolokia-based poller");
        main.run();
    }

}
