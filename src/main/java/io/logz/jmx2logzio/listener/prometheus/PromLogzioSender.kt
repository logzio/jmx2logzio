package io.logz.jmx2logzio.listener.prometheus

import io.logz.prometheus.protobuf.Types
import io.logz.sender.HttpsRequestConfiguration
import io.logz.sender.SenderStatusReporter
import io.logz.sender.exceptions.LogzioServerErrorException
import java.util.concurrent.ExecutionException
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import java.util.concurrent.atomic.AtomicBoolean

class PromLogzioSender(
    private val reporter: SenderStatusReporter,
    private val tasksExecutor: ScheduledExecutorService,
    private val timeSeriesQueue: TimeSeriesDiskQueue,
    private val debug: Boolean = false,
    private val drainTimeoutSec: Long = 5,
    httpsRequestConfiguration: HttpsRequestConfiguration
) {
    companion object {
        private const val MAX_SIZE_IN_BYTES = 3 * 1024 * 1024 // 3 MB
        private const val FINAL_DRAIN_TIMEOUT_SEC = 20
    }

    private val promHttpsSyncSender = PromHttpsSyncSender(httpsRequestConfiguration, reporter)
    private val drainRunning = AtomicBoolean(false)

    init {
        debug("Created new LogzioPrometheusSender class")
    }

    fun start() {
        tasksExecutor.scheduleAtFixedRate(this::drainQueueAndSend, 0, drainTimeoutSec, TimeUnit.SECONDS)
    }

    fun stop() {
        // Creating a scheduled executor, outside of logback to try and drain the queue one last time
        val executorService = Executors.newSingleThreadExecutor()
        debug("Got stop request, Submitting a final drain queue task to drain before shutdown. Will timeout in $FINAL_DRAIN_TIMEOUT_SEC seconds.")

        try {
            executorService.submit { this.drainQueue() }[FINAL_DRAIN_TIMEOUT_SEC.toLong(), TimeUnit.SECONDS]
        } catch (e: InterruptedException) {
            debug("Waited $FINAL_DRAIN_TIMEOUT_SEC seconds, but could not finish draining. quitting.", e)
        } catch (e: ExecutionException) {
            debug("Waited $FINAL_DRAIN_TIMEOUT_SEC seconds, but could not finish draining. quitting.", e)
        } catch (e: TimeoutException) {
            debug("Waited $FINAL_DRAIN_TIMEOUT_SEC seconds, but could not finish draining. quitting.", e)
        } finally {
            executorService.shutdownNow()
        }
    }

    fun send(message: ByteArray) {
        timeSeriesQueue.enqueue(message)
    }

    fun drainQueueAndSend() {
        try {
            if (drainRunning.get()) {
                debug("Drain is running so we won't run another one in parallel")
                return
            } else {
                drainRunning.set(true)
            }
            drainQueue()
        } catch (e: Exception) {
            // We cant throw anything out, or the task will stop, so just swallow all
            reporter.error("Uncaught error from LogzioPrometheusSender", e)
        } finally {
            drainRunning.set(false)
        }
    }

    private fun drainQueue() {
        debug("Attempting to drain queue")
        if (!timeSeriesQueue.isEmpty()) {
            while (!timeSeriesQueue.isEmpty()) {
                val timeSeriesList = dequeueUpToMaxBatchSize()
                try {
                    promHttpsSyncSender.sendToLogzio(timeSeriesList)
                } catch (e: LogzioServerErrorException) {
                    debug("Could not send log to logz.io: ", e)
                    debug("Will retry in the next interval")

                    // And lets return everything to the queue
                    timeSeriesList.forEach{ timeSeriesQueue.enqueue(it.toByteArray()) }

                    // Lets wait for a new interval, something is wrong in the server side
                    break
                }
                if (Thread.interrupted()) {
                    debug("Stopping drainQueue to thread being interrupted")
                    break
                }
            }
        }
    }

    private fun dequeueUpToMaxBatchSize(): List<Types.TimeSeries> {
        val timeSeriesList = mutableListOf<Types.TimeSeries>()
        var totalSize = 0

        while (!timeSeriesQueue.isEmpty() && totalSize < MAX_SIZE_IN_BYTES) {
            val rawTimeSeries = timeSeriesQueue.dequeue()
            if (rawTimeSeries != null && rawTimeSeries.isNotEmpty()) {
                timeSeriesList.add(Types.TimeSeries.parseFrom(rawTimeSeries))
                totalSize += rawTimeSeries.size
            }
        }

        return timeSeriesList
    }

    private fun debug(message: String) {
        if (debug) {
            reporter.info("DEBUG: $message")
        }
    }

    private fun debug(message: String, e: Throwable) {
        if (debug) {
            reporter.info("DEBUG: $message", e)
        }
    }
}