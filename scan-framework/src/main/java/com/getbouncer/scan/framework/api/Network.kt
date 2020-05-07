@file:JvmName("Network")
package com.getbouncer.scan.framework.api

import android.util.Log
import com.getbouncer.scan.framework.Config
import com.getbouncer.scan.framework.NetworkConfig
import com.getbouncer.scan.framework.time.Timer
import com.getbouncer.scan.framework.util.retry
import java.io.InputStreamReader
import java.io.OutputStream
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.util.zip.GZIPOutputStream
import kotlinx.serialization.KSerializer

private const val REQUEST_METHOD_GET = "GET"
private const val REQUEST_METHOD_POST = "POST"

private const val REQUEST_PROPERTY_AUTHENTICATION = "x-bouncer-auth"
private const val REQUEST_PROPERTY_CONTENT_TYPE = "Content-Type"
private const val REQUEST_PROPERTY_CONTENT_ENCODING = "Content-Encoding"

private const val CONTENT_TYPE_JSON = "application/json; utf-8"
private const val CONTENT_ENCODING_GZIP = "gzip"

/**
 * The size of a TCP network packet. If smaller than this, there is no benefit to GZIP.
 */
private const val GZIP_MIN_SIZE_BYTES = 1500

private val networkTimer by lazy { Timer.newInstance(Config.logTag, "network") }

/**
 * Send a post request to a bouncer endpoint.
 */
suspend fun <Request, Response, Error> postForResult(
    path: String,
    data: Request,
    requestSerializer: KSerializer<Request>,
    responseSerializer: KSerializer<Response>,
    errorSerializer: KSerializer<Error>
): NetworkResult<Response, Error> =
    try {
        translateNetworkResult(
            postJsonWithRetries(
                path = path,
                jsonData = Config.json.stringify(requestSerializer, data)
            ), responseSerializer, errorSerializer
        )
    } catch (t: Throwable) {
        NetworkResult.Exception(responseCode = -1, exception = t)
    }

suspend fun <Response, Error> getForResult(
    path: String,
    responseSerializer: KSerializer<Response>,
    errorSerializer: KSerializer<Error>
): NetworkResult<Response, Error> =
    try {
        translateNetworkResult(getWithRetries(path), responseSerializer, errorSerializer)
    } catch (t: Throwable) {
        NetworkResult.Exception(responseCode = -1, exception = t)
    }

/**
 * Translate a string network result to a response or error.
 */
private fun <Response, Error> translateNetworkResult(
    networkResult: NetworkResult<String, String>,
    responseSerializer: KSerializer<Response>,
    errorSerializer: KSerializer<Error>
): NetworkResult<Response, Error> = when (networkResult) {
    is NetworkResult.Success -> try {
        NetworkResult.Success<Response, Error>(
            responseCode = networkResult.responseCode,
            body = Config.json.parse(responseSerializer, networkResult.body)
        )
    } catch (t: Throwable) {
        try {
            NetworkResult.Error<Response, Error>(
                responseCode = networkResult.responseCode,
                error = Config.json.parse(errorSerializer, networkResult.body)
            )
        } catch (et: Throwable) {
            throw t
        }
    }
    is NetworkResult.Error -> NetworkResult.Error(
        responseCode = networkResult.responseCode,
        error = Config.json.parse(errorSerializer, networkResult.error)
    )
    is NetworkResult.Exception -> NetworkResult.Exception(
        responseCode = networkResult.responseCode,
        exception = networkResult.exception
    )
}

/**
 * Send a post request to a bouncer endpoint and ignore the response.
 */
suspend fun <Request> postData(
    path: String,
    data: Request,
    requestSerializer: KSerializer<Request>
) {
    postJsonWithRetries(
        path = path,
        jsonData = Config.json.stringify(requestSerializer, data)
    )
}

/**
 * Send a post request to a bouncer endpoint with retries.
 */
suspend fun postJsonWithRetries(path: String, jsonData: String): NetworkResult<String, String> =
    try {
        retry(
            retryDelay = NetworkConfig.retryDelay,
            times = NetworkConfig.retryTotalAttempts
        ) {
            val result = postJson(path, jsonData)
            if (result.responseCode in NetworkConfig.retryStatusCodes) {
                throw RetryNetworkRequestException(result)
            } else {
                result
            }
        }
    } catch (e: RetryNetworkRequestException) {
        e.result
    }

/**
 * Send a get request to a bouncer endpoint with retries.
 */
suspend fun getWithRetries(path: String): NetworkResult<String, String> =
    try {
        retry(
            retryDelay = NetworkConfig.retryDelay,
            times = NetworkConfig.retryTotalAttempts
        ) {
            val result = get(path)
            if (result.responseCode in NetworkConfig.retryStatusCodes) {
                throw RetryNetworkRequestException(result)
            } else {
                result
            }
        }
    } catch (e: RetryNetworkRequestException) {
        e.result
    }

/**
 * Send a post request to a bouncer endpoint.
 */
private fun postJson(
    path: String,
    jsonData: String
): NetworkResult<String, String> = networkTimer.measure(path) {
    val fullPath = if (path.startsWith("/")) path else "/$path"
    val url = URL("${getBaseUrl()}$fullPath")
    var responseCode = -1

    try {
        with(url.openConnection() as HttpURLConnection) {
            requestMethod = REQUEST_METHOD_POST

            // Set the connection to both send and receive data
            doOutput = true
            doInput = true

            // Set headers
            setRequestProperty(REQUEST_PROPERTY_AUTHENTICATION, Config.apiKey)
            setRequestProperty(REQUEST_PROPERTY_CONTENT_TYPE, CONTENT_TYPE_JSON)

            // Write the data
            if (NetworkConfig.useCompression && jsonData.toByteArray().size >= GZIP_MIN_SIZE_BYTES) {
                setRequestProperty(REQUEST_PROPERTY_CONTENT_ENCODING, CONTENT_ENCODING_GZIP)
                writeGzipData(
                    outputStream,
                    jsonData
                )
            } else {
                writeData(
                    outputStream,
                    jsonData
                )
            }

            // Read the response code. This will block until the response has been received.
            responseCode = this.responseCode

            // Read the response
            when (responseCode) {
                in 200 until 300 -> NetworkResult.Success(
                    responseCode,
                    readResponse(this)
                )
                else -> NetworkResult.Error(
                    responseCode,
                    readResponse(this)
                )
            }
        }
    } catch (t: Throwable) {
        Log.w(Config.logTag, "Failed network request to endpoint $url", t)
        NetworkResult.Exception(responseCode, t)
    }
}

/**
 * Send a get request to a bouncer endpoint.
 */
private fun get(path: String): NetworkResult<String, String> = networkTimer.measure(path) {
    val fullPath = if (path.startsWith("/")) path else "/$path"
    val url = URL("${getBaseUrl()}$fullPath")
    var responseCode = -1

    try {
        with(url.openConnection() as HttpURLConnection) {
            requestMethod = REQUEST_METHOD_GET

            // Set the connection to both send and receive data
            doOutput = false
            doInput = true

            // Set headers
            setRequestProperty(REQUEST_PROPERTY_AUTHENTICATION, Config.apiKey)

            // Read the response code. This will block until the response has been received.
            responseCode = this.responseCode

            // Read the response
            when (responseCode) {
                in 200 until 300 -> NetworkResult.Success(
                    responseCode,
                    readResponse(this)
                )
                else -> NetworkResult.Error(
                    responseCode,
                    readResponse(this)
                )
            }
        }
    } catch (t: Throwable) {
        Log.w(Config.logTag, "Failed network request to endpoint $url", t)
        NetworkResult.Exception(responseCode, t)
    }
}

private fun writeGzipData(outputStream: OutputStream, data: String) {
    OutputStreamWriter(
        GZIPOutputStream(
            outputStream
        )
    ).use {
        it.write(data)
        it.flush()
    }
}

private fun writeData(outputStream: OutputStream, data: String) {
    OutputStreamWriter(outputStream).use {
        it.write(data)
        it.flush()
    }
}

private fun readResponse(connection: HttpURLConnection): String =
        InputStreamReader(connection.inputStream).use {
            it.readLines().joinToString(separator = "\n")
        }

/**
 * Get the [NetworkConfig.baseUrl] with no trailing slashes.
 */
private fun getBaseUrl() = if (NetworkConfig.baseUrl.endsWith("/")) {
    NetworkConfig.baseUrl.substring(0, NetworkConfig.baseUrl.length - 1)
} else {
    NetworkConfig.baseUrl
}

private class RetryNetworkRequestException(val result: NetworkResult<String, String>) : Exception()
