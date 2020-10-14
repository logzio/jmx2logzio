package io.logz.jmx2logzio.listener.prometheus

import io.logz.jmx2logzio.Utils.HangupInterceptor
import io.logz.jmx2logzio.listener.ListenerWriter
import io.logz.jmx2logzio.objects.LogzioJavaSenderParams
import io.logz.jmx2logzio.objects.Metric
import io.logz.jmx2logzio.objects.StatusReporterFactory
import io.logz.prometheus.protobuf.Types
import io.logz.prometheus.protobuf.Types.Sample
import io.logz.prometheus.protobuf.Types.TimeSeries
import io.logz.sender.HttpsRequestConfiguration
import io.logz.sender.exceptions.LogzioParameterErrorException
import org.slf4j.LoggerFactory
import java.io.IOException
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit
import javax.annotation.PostConstruct

class PromListenerWriter(
    senderParams: LogzioJavaSenderParams
) : ListenerWriter {
    companion object {
        private val logger = LoggerFactory.getLogger(PromListenerWriter::class.java)
    }

    private val senderExecutors: ScheduledExecutorService = Executors.newScheduledThreadPool(senderParams.threadPoolSize);
    private val sender: PromLogzioSender = createSender(senderExecutors, senderParams).also {
        it.start()
    }

    private fun createSender(senderExecutors: ScheduledExecutorService, logzioSenderParams: LogzioJavaSenderParams): PromLogzioSender {
        try {
            val statusReporter = StatusReporterFactory.newSenderStatusReporter(LoggerFactory.getLogger(logzioSenderParams.loggerName))

            val requestConf = HttpsRequestConfiguration
                .builder()
                .setLogzioListenerUrl(logzioSenderParams.url)
                .setLogzioToken(logzioSenderParams.token)
                .build()

            val diskQueue = TimeSeriesDiskQueue(logzioSenderParams.queueDir,
                reporter = statusReporter,
                executorService = senderExecutors,
                fsPercentThreshold = logzioSenderParams.fileSystemFullPercentThreshold,
                gcPersistedQueueFilesIntervalSeconds = logzioSenderParams.gcPersistedQueueFilesIntervalSeconds)

            return PromLogzioSender(statusReporter, senderExecutors, diskQueue, httpsRequestConfiguration = requestConf)
        } catch (e: LogzioParameterErrorException) {
            throw RuntimeException("problem in one or more parameters with error ${e.message}", e)
        }
    }

    override fun writeMetrics(metrics: List<Metric>) {
        logger.debug("sending ${metrics.size} metrics")
        try {
            metrics.forEach {
                val timeSeriesList = buildTimeSeriesList(it)
                timeSeriesList.forEach { ts -> sender.send(ts) }
            }
        } catch (e: IOException) {
            logger.error("error sending metrics: ${e.message}\n${e.printStackTrace()}")
        }
    }

    @PostConstruct
    override fun start() {
        enableHangupSupport()
    }

    override fun shutdown() {
        senderExecutors.shutdown()
        try {
            senderExecutors.awaitTermination(20, TimeUnit.SECONDS)
        } catch (e: InterruptedException) {
            logger.error("Shutdown was interrupted {}", e.message, e)
        }
        if (!senderExecutors.isTerminated) {
            senderExecutors.shutdownNow()
        }
        logger.info("Closing Listener Writer...")
    }

    private fun buildTimeSeriesList(metric: Metric): List<TimeSeries> {
        val metrics = metric.metricMap
        val labels = metric.dimensionsMap.map { (key, value) -> buildLabel(key, value.toString()) }

        val timeSeriesList = metrics.map { (name, value) ->
            TimeSeries.newBuilder()
                .addLabels(buildLabel("__name__", name))
                .addAllLabels(labels)
                .addSamples(buildSample(metric.timestamp.toEpochMilli(), value.toDouble()))
                .build()
        }
        return timeSeriesList
    }

    private fun buildLabel(labelName: String, labelValue: String) =
        Types.Label.newBuilder()
            .setName(labelName)
            .setValue(labelValue)
            .build()

    private fun buildSample(timestamp: Long, value: Double) =
        Sample.newBuilder()
            .setTimestampMillis(timestamp)
            .setValue(value)
            .build()

    /**
     * Enable graceful shutdown
     */
    private fun enableHangupSupport() {
        val interceptor = HangupInterceptor(this)
        Runtime.getRuntime().addShutdownHook(interceptor)
    }
}