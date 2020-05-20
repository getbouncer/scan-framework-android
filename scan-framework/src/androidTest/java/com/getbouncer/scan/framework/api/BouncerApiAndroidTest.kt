package com.getbouncer.scan.framework.api

import androidx.test.filters.LargeTest
import androidx.test.platform.app.InstrumentationRegistry
import com.getbouncer.scan.framework.Config
import com.getbouncer.scan.framework.Stats
import com.getbouncer.scan.framework.api.dto.AppInfo
import com.getbouncer.scan.framework.api.dto.BouncerErrorResponse
import com.getbouncer.scan.framework.api.dto.ClientDevice
import com.getbouncer.scan.framework.api.dto.ModelSignedUrlResponse
import com.getbouncer.scan.framework.api.dto.ScanStatistics
import com.getbouncer.scan.framework.api.dto.StatsPayload
import com.getbouncer.scan.framework.api.dto.ValidateApiKeyResponse
import com.getbouncer.scan.framework.util.AppDetails
import com.getbouncer.scan.framework.util.Device
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.fail
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runBlockingTest
import kotlinx.serialization.Serializable
import org.junit.After
import org.junit.Before
import org.junit.Test

class BouncerApiAndroidTest {

    companion object {
        private const val STATS_PATH = "/scan_stats"
    }

    private val testContext = InstrumentationRegistry.getInstrumentation().context

    @Before
    fun before() {
        Config.apiKey = "uXDc2sbugrkmvj1Bm3xOTXBw7NW4llgn"
    }

    @After
    fun after() {
        Config.apiKey = null
    }

    @Test
    @LargeTest
    @ExperimentalCoroutinesApi
    fun uploadScanStats_success() = runBlockingTest {
        for (i in 0..100) {
            Stats.trackRepeatingTask("test_repeating_task_1").trackResult("$i")
        }

        for (i in 0..100) {
            Stats.trackRepeatingTask("test_repeating_task_2").trackResult("$i")
        }

        val task1 = Stats.trackTask("test_task_1")
        for (i in 0..5) {
            task1.trackResult("$i")
        }

        when (val result = postForResult(
            path = STATS_PATH,
            data = StatsPayload(
                instanceId = "test_instance_id",
                scanId = "test_scan_id",
                device = ClientDevice.fromDevice(Device.fromContext(testContext)),
                app = AppInfo.fromAppDetails(AppDetails.fromContext(testContext)),
                scanStats = ScanStatistics.fromStats()
            ),
            requestSerializer = StatsPayload.serializer(),
            responseSerializer = ScanStatsResults.serializer(),
            errorSerializer = BouncerErrorResponse.serializer()
        )) {
            is NetworkResult.Success<ScanStatsResults, BouncerErrorResponse> -> {
                assertEquals(200, result.responseCode)
            }
            else -> fail("Network result was not success: $result")
        }
    }

    @Test
    @LargeTest
    @ExperimentalCoroutinesApi
    fun validateApiKey() = runBlockingTest {
        when (val result = com.getbouncer.scan.framework.api.validateApiKey()) {
            is NetworkResult.Success<ValidateApiKeyResponse, BouncerErrorResponse> -> {
                assertEquals(200, result.responseCode)
            }
            else -> fail("network result was not success: $result")
        }
    }

    /**
     * Note, if this test is failing with an unauthorized exception, please make sure that the API
     * key specified at the top of this file is authorized with DOWNLOAD_VERIFY_MODELS
     */
    @Test
    @LargeTest
    @ExperimentalCoroutinesApi
    fun getModelSignedUrl() = runBlockingTest {
        when (val result = getModelSignedUrl(
            "fake_model",
            "v0.0.1",
            "model.tflite"
        )) {
            is NetworkResult.Success<ModelSignedUrlResponse, BouncerErrorResponse> -> {
                assertNotNull(result.body.modelUrl)
                assertNotEquals("", result.body.modelUrl)
            }
            else -> fail("network result was not success: $result")
        }
    }

    @Serializable
    data class ScanStatsResults(val status: String? = "")
}
