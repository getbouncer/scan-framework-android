package com.getbouncer.scan.framework

import android.content.Context
import com.getbouncer.scan.framework.exception.InvalidBouncerApiKeyException
import com.getbouncer.scan.framework.time.Duration
import com.getbouncer.scan.framework.time.seconds
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonConfiguration

private const val REQUIRED_API_KEY_LENGTH = 32

object Config {

    /**
     * If set to true, turns on debug information.
     */
    @JvmStatic
    var isDebug: Boolean = false

    /**
     * A log tag used by this library.
     */
    @JvmStatic
    var logTag: String = "Bouncer"

    /**
     * The API key to interface with Bouncer servers
     */
    @JvmStatic
    var apiKey: String? = null
        set(value) {
            if (value != null && value.length != REQUIRED_API_KEY_LENGTH) {
                throw InvalidBouncerApiKeyException
            }
            field = value
        }

    /**
     * The JSON configuration to use throughout this SDK.
     */
    @JvmStatic
    var json: Json = Json(JsonConfiguration.Stable.copy(ignoreUnknownKeys = false))

    /**
     * Whether or not to track stats
     */
    @JvmStatic
    val trackStats: Boolean = true

    /**
     * The application context. If [initialize] has not been called, this will be null.
     */
    var applicationContext: Context? = null
        private set

    /**
     * Get the application context. This must be pre-initialized.
     */
    fun getAppContext(): Context = applicationContext.let {
        checkNotNull(it) { "Config.initialize was not called." }
        it
    }

    /**
     *
     */
    @JvmStatic
    fun initialize(context: Context, apiKey: String) {
        this.apiKey = apiKey
        this.applicationContext = context.applicationContext
    }
}

object NetworkConfig {

    /**
     * The base URL where all network requests will be sent
     */
    @JvmStatic
    var baseUrl = "https://api.getbouncer.com"

    /**
     * Whether or not to compress network request bodies.
     */
    @JvmStatic
    var useCompression: Boolean = false

    /**
     * The total number of times to try making a network request.
     */
    @JvmStatic
    var retryTotalAttempts: Int = 3

    /**
     * The delay between network request retries.
     */
    @JvmStatic
    var retryDelay: Duration = 5.seconds

    /**
     * Status codes that should be retried from bouncer servers.
     */
    @JvmStatic
    var retryStatusCodes: Iterable<Int> = 500..599
}
