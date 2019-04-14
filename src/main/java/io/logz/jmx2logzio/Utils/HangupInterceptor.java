package io.logz.jmx2logzio.Utils;
import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import org.slf4j.LoggerFactory;

/**
 * A class for intercepting the hang up signal and do a graceful shutdown of the Camel.
 */
public final class HangupInterceptor extends Thread {
    private Logger logger = (Logger) LoggerFactory.getLogger(HangupInterceptor.class);
    private Shutdownable main;

    public HangupInterceptor(Shutdownable main) {
        this.main = main;
    }

    @Override
    public void run() {
        logger.info("Received hang up - stopping...");
        try {
            main.shutdown();
        } catch (Exception ex) {
            logger.error("Error during stopping main", ex);
        }
    }
}