@file:JvmName("Network")
package com.getbouncer.scan.framework.api

import android.content.Context
import android.util.Base64
import android.util.Log
import com.getbouncer.scan.framework.Config
import com.getbouncer.scan.framework.NetworkConfig
import com.getbouncer.scan.framework.time.Timer
import com.getbouncer.scan.framework.util.DeviceIds
import com.getbouncer.scan.framework.util.getDeviceName
import com.getbouncer.scan.framework.util.getOsVersion
import com.getbouncer.scan.framework.util.getPlatform
import com.getbouncer.scan.framework.util.getSdkFlavor
import com.getbouncer.scan.framework.util.getSdkVersion
import com.getbouncer.scan.framework.util.memoize
import com.getbouncer.scan.framework.util.retry
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import java.io.InputStreamReader
import java.io.OutputStream
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.util.zip.GZIPOutputStream

private const val REQUEST_METHOD_GET = "GET"
private const val REQUEST_METHOD_POST = "POST"

private const val REQUEST_PROPERTY_AUTHENTICATION = "x-bouncer-auth"
private const val REQUEST_PROPERTY_DEVICE_ID = "x-bouncer-device-id"
private const val REQUEST_PROPERTY_USER_AGENT = "User-Agent"
private const val REQUEST_PROPERTY_CONTENT_TYPE = "Content-Type"
private const val REQUEST_PROPERTY_CONTENT_ENCODING = "Content-Encoding"

private const val CONTENT_TYPE_JSON = "application/json; utf-8"
private const val CONTENT_ENCODING_GZIP = "gzip"

/**
 * The size of a TCP network packet. If smaller than this, there is no benefit to GZIP.
 */
private const val GZIP_MIN_SIZE_BYTES = 1500

private val networkTimer by lazy { Timer.newInstance(Config.logTag, "network") }

private val userAgent by lazy { "bouncer/${getPlatform()}/${getDeviceName()}/${getOsVersion()}/${getSdkVersion()}/${getSdkFlavor()}" }

/**
 * Send a post request to a bouncer endpoint.
 */
suspend fun <Request, Response, Error> postForResult(
    context: Context,
    path: String,
    data: Request,
    requestSerializer: KSerializer<Request>,
    responseSerializer: KSerializer<Response>,
    errorSerializer: KSerializer<Error>
): NetworkResult<Response, Error> =
    translateNetworkResult(
        networkResult = postJsonWithRetries(
            context = context,
            path = path,
            jsonData = Config.json.stringify(requestSerializer, data)
        ),
        responseSerializer = responseSerializer,
        errorSerializer = errorSerializer
    )

/**
 * Send a post request to a bouncer endpoint and ignore the response.
 */
suspend fun <Request> postData(
    context: Context,
    path: String,
    data: Request,
    requestSerializer: KSerializer<Request>
) {
    postJsonWithRetries(
        context = context,
        path = path,
        jsonData = Config.json.stringify(requestSerializer, data)
    )
}

/**
 * Send a get request to a bouncer endpoint and parse the response.
 */
suspend fun <Response, Error> getForResult(
    context: Context,
    path: String,
    responseSerializer: KSerializer<Response>,
    errorSerializer: KSerializer<Error>
): NetworkResult<Response, Error> =
    translateNetworkResult(getWithRetries(context, path), responseSerializer, errorSerializer)

/**
 * Translate a string network result to a response or error.
 */
private fun <Response, Error> translateNetworkResult(
    networkResult: NetworkResult<String, String>,
    responseSerializer: KSerializer<Response>,
    errorSerializer: KSerializer<Error>
): NetworkResult<Response, Error> = when (networkResult) {
    is NetworkResult.Success ->
        try {
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
                NetworkResult.Exception<Response, Error>(networkResult.responseCode, t)
            }
        }
    is NetworkResult.Error ->
        try {
            NetworkResult.Error<Response, Error>(
                responseCode = networkResult.responseCode,
                error = Config.json.parse(errorSerializer, networkResult.error)
            )
        } catch (t: Throwable) {
            NetworkResult.Exception<Response, Error>(networkResult.responseCode, t)
        }
    is NetworkResult.Exception ->
        NetworkResult.Exception(
            responseCode = networkResult.responseCode,
            exception = networkResult.exception
        )
}

/**
 * Send a post request to a bouncer endpoint with retries.
 */
private suspend fun postJsonWithRetries(
    context: Context,
    path: String,
    jsonData: String
): NetworkResult<String, String> =
    try {
        retry(
            retryDelay = NetworkConfig.retryDelay,
            times = NetworkConfig.retryTotalAttempts
        ) {
            val result = postJson(context, path, jsonData)
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
private suspend fun getWithRetries(context: Context, path: String): NetworkResult<String, String> =
    try {
        retry(
            retryDelay = NetworkConfig.retryDelay,
            times = NetworkConfig.retryTotalAttempts
        ) {
            val result = get(context, path)
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
    context: Context,
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
            setRequestHeaders(context)
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
private fun get(context: Context, path: String): NetworkResult<String, String> = networkTimer.measure(path) {
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
            setRequestHeaders(context)

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
 * Set the required request headers on an HttpURLConnection
 */
private fun HttpURLConnection.setRequestHeaders(context: Context) {
    setRequestProperty(REQUEST_PROPERTY_AUTHENTICATION, Config.apiKey)
    setRequestProperty(REQUEST_PROPERTY_USER_AGENT, userAgent)
    setRequestProperty(REQUEST_PROPERTY_DEVICE_ID, buildDeviceId(context))
}

@Serializable
private data class DeviceIdStructure(
    /**
     * android_id
     */
    val a: String,

    /**
     * vendor_id
     */
    val v: String,

    /**
     * advertising_id
     */
    val d: String
)

private val buildDeviceId = memoize { context: Context ->
    DeviceIds.fromContext(context).run {
        Base64.encodeToString(
            Config.json.stringify(
                DeviceIdStructure.serializer(),
                DeviceIdStructure(a = androidId ?: "", v = "", d = "")
            ).toByteArray(Charsets.UTF_8),
            Base64.URL_SAFE
        )
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
