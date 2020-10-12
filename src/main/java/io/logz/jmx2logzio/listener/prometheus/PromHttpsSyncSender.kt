package io.logz.jmx2logzio.listener.prometheus

import io.logz.prometheus.protobuf.Remote
import io.logz.prometheus.protobuf.Types
import io.logz.sender.HttpsRequestConfiguration
import io.logz.sender.SenderStatusReporter
import io.logz.sender.exceptions.LogzioServerErrorException
import org.apache.http.HttpHeaders
import org.xerial.snappy.Snappy
import java.io.BufferedReader
import java.io.IOException
import java.io.InputStreamReader
import java.net.HttpURLConnection

class PromHttpsSyncSender(
    private val configuration: HttpsRequestConfiguration,
    private val reporter: SenderStatusReporter
) {
    fun sendToLogzio(timeSeriesList: List<Types.TimeSeries>) {
        try {
            val writeRequest = Remote.WriteRequest.newBuilder()
                .addAllTimeseries(timeSeriesList)
                .build()
            val payload: ByteArray = Snappy.compress(writeRequest.toByteArray())
            var currentRetrySleep = configuration.initialWaitBeforeRetryMS.toLong()

            for (currTry in 1..configuration.maxRetriesAttempts) {
                var responseCode = 0
                var responseMessage = ""

                try {
                    val conn = sendRequest(payload)
                    responseCode = conn.responseCode
                    responseMessage = conn.responseMessage

                    if (responseCode == HttpURLConnection.HTTP_OK) {
                        reporter.info("Successfully sent bulk to logz.io, size: " + payload.size)
                        break
                    }

                    if (!shouldRetry(responseCode, responseMessage, conn)) break

                    handleRetry(currentRetrySleep, currTry, responseCode, responseMessage, null)
                } catch (e: IOException) {
                    reporter.error("Got IO exception - " + e.message)
                    handleRetry(currentRetrySleep, currTry, responseCode, responseMessage, e)
                }

                currentRetrySleep *= 2
            }
        } catch (e: InterruptedException) {
            reporter.info("Got interrupted exception")
            Thread.currentThread().interrupt()
        }
    }

    private fun sendRequest(payload: ByteArray): HttpURLConnection {
        val conn = configuration.logzioListenerUrl.openConnection() as HttpURLConnection
        conn.requestMethod = conn.requestMethod
        conn.setRequestProperty(HttpHeaders.CONTENT_LENGTH, payload.size.toString())
        conn.setRequestProperty(HttpHeaders.CONTENT_TYPE, "application/x-protobuf")
        conn.setRequestProperty(HttpHeaders.CONTENT_ENCODING, "snappy")
        conn.setRequestProperty(HttpHeaders.AUTHORIZATION, "Bearer ${configuration.logzioToken}")

        conn.readTimeout = configuration.socketTimeout
        conn.connectTimeout = configuration.connectTimeout
        conn.doOutput = true
        conn.doInput = true
        conn.outputStream.write(payload)

        return conn
    }

    private fun shouldRetry(responseCode: Int, responseMessage: String, conn: HttpURLConnection): Boolean {
        return when(responseCode) {
            HttpURLConnection.HTTP_BAD_REQUEST -> {
                reporter.warning(readErrorStream(conn))
                false
            }
            HttpURLConnection.HTTP_UNAUTHORIZED -> {
                reporter.error("Logz.io: Got forbidden! Your token is not right. Unfortunately, dropping logs. Message: $responseMessage")
                false
            }
            else -> true
        }
    }

    private fun handleRetry(retrySleep: Long, currTry: Int, responseCode: Int, responseMessage: String, savedException: IOException?) {
        if (currTry >= configuration.maxRetriesAttempts) {
            savedException?.run { reporter.error("Got IO exception on the last bulk try to logz.io", savedException) }

            // Giving up, something is broken on Logz.io side, we will try again later
            throw LogzioServerErrorException("Got HTTP $responseCode code from logz.io, with message: $responseMessage")
        }

        reporter.warning("Could not send log to logz.io, retry ($currTry/${configuration.maxRetriesAttempts})")
        reporter.warning("Sleeping for $retrySleep ms and will try again.")
        Thread.sleep(retrySleep)
    }

    private fun readErrorStream(conn: HttpURLConnection): String {
        var bufferedReader: BufferedReader? = null

        val problemDescription = StringBuilder()
        val errorStream = conn.errorStream
        if (errorStream != null) {
            try {
                bufferedReader = BufferedReader(InputStreamReader(errorStream))
                bufferedReader.lines().forEach { line: String? -> problemDescription.append("\n").append(line) }
                return "Got 400 from logzio, here is the output: $problemDescription"
            } finally {
                try {
                    bufferedReader?.close()
                } catch (ignored: Exception) { }
            }
        }

        return "Got 400 from logzio, couldn't read the output"
    }
}
