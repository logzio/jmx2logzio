package io.logz.jmx2logzio.listener.prometheus

import io.logz.sender.SenderStatusReporter
import io.logz.sender.com.bluejeans.common.bigqueue.BigQueue
import io.logz.sender.exceptions.LogzioParameterErrorException
import java.io.Closeable
import java.io.File
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit

class TimeSeriesDiskQueue(
    private val queueDir: File,
    private val reporter: SenderStatusReporter,
    private val executorService: ScheduledExecutorService,
    private val dontCheckEnoughDiskSpace: Boolean = false,
    private val fsPercentThreshold: Int = 98,
    private val gcPersistedQueueFilesIntervalSeconds: Int = 30,
    private val checkDiskSpaceInterval: Int = 1000
): Closeable {
    private val timeSeriesQueue: BigQueue

    @Volatile
    private var isEnoughSpace = true

    init {
        // divide bufferDir to dir and queue name
        val dir = queueDir.absoluteFile.parent
        val queueNameDir = queueDir.name
        if (dir == null || queueNameDir.isEmpty()) {
            throw LogzioParameterErrorException("queueDir", " value is empty: " + queueDir.absolutePath)
        }

        timeSeriesQueue = BigQueue(dir, queueNameDir)
        executorService.scheduleWithFixedDelay(Runnable { this.gcBigQueue() }, 0, gcPersistedQueueFilesIntervalSeconds.toLong(), TimeUnit.SECONDS)
        executorService.scheduleWithFixedDelay(Runnable { this.validateEnoughSpace() }, 0, checkDiskSpaceInterval.toLong(), TimeUnit.MILLISECONDS)
    }

    fun enqueue(timeSeries: ByteArray) {
        if (isEnoughSpace) {
            timeSeriesQueue.enqueue(timeSeries)
        }
    }

    fun dequeue(): ByteArray? {
        return timeSeriesQueue.dequeue()
    }

    fun isEmpty(): Boolean {
        return timeSeriesQueue.isEmpty
    }

    private fun validateEnoughSpace() {
        try {
            if (dontCheckEnoughDiskSpace) return

            val actualUsedFsPercent = 100 - (queueDir.usableSpace.toDouble() / queueDir.totalSpace.toDouble() * 100).toInt()
            if (actualUsedFsPercent >= fsPercentThreshold) {
                if (isEnoughSpace) {
                    reporter.warning("Logz.io: Dropping logs, as FS used space on ${queueDir.absolutePath} is $actualUsedFsPercent percent, and the drop threshold is $fsPercentThreshold percent")
                }
                isEnoughSpace = false
            } else {
                isEnoughSpace = true
            }
        } catch (e: Throwable) {
            reporter.error("Uncaught error from validateEnoughSpace()", e)
        }
    }

    private fun gcBigQueue() {
        try {
            timeSeriesQueue.gc()
        } catch (e: Throwable) {
            // We cant throw anything out, or the task will stop, so just swallow all
            reporter.error("Uncaught error from BigQueue.gc()", e)
        }
    }

    override fun close() {
        gcBigQueue()
    }
}