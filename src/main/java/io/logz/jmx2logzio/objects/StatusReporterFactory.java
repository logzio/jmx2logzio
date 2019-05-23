package io.logz.jmx2logzio.objects;

import io.logz.sender.SenderStatusReporter;
import org.slf4j.Logger;

public class StatusReporterFactory {

    public static SenderStatusReporter newSenderStatusReporter(Logger logger) {
        return new SenderStatusReporter() {
            @Override
            public void error(String s) {
                logger.error(s);
            }

            @Override
            public void error(String s, Throwable throwable) {
                logger.error(s + " " + throwable.getMessage());
            }

            @Override
            public void warning(String s) {
                logger.warn(s);
            }

            @Override
            public void warning(String s, Throwable throwable) {
                logger.warn(s + " " + throwable.getMessage());
            }

            @Override
            public void info(String s) {
                logger.debug(s);
            }

            @Override
            public void info(String s, Throwable throwable) {
                logger.debug(s + " " + throwable.getMessage());
            }
        };
    }
}
