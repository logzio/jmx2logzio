package io.logz.jmx2logzio.objects;

import ch.qos.logback.classic.Logger;
import io.logz.sender.SenderStatusReporter;

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
                if (s.contains("DEBUG:"))  {
                    logger.debug(s);
                } else {
                    logger.info(s);
                }
            }

            @Override
            public void info(String s, Throwable throwable) {
                if (s.contains("DEBUG:"))  {
                    logger.debug(s + " " + throwable.getMessage());
                } else {
                    logger.info(s + " " + throwable.getMessage());
                }
            }
        };
    }
}
