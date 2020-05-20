package com.getbouncer.scan.framework.api

import androidx.test.filters.LargeTest
import com.getbouncer.scan.framework.Config
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runBlockingTest
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.test.fail
import org.junit.After
import org.junit.Before
import org.junit.Test

class BouncerApiTest {

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
    fun validateApiKey() = runBlockingTest {
        when (val result = com.getbouncer.scan.framework.api.validateApiKey()) {
            is NetworkResult.Success -> {
                assertTrue(result.body.isApiKeyValid)
                assertNull(result.body.keyInvalidReason)
            }
            else -> fail("Network result was not success $result")
        }
    }

    @Test
    @LargeTest
    @ExperimentalCoroutinesApi
    fun getModelSignedUrl() = runBlockingTest {
        when (val result = getModelSignedUrl(
            "fake_model",
            "v0.0.1",
            "model.tflite"
        )) {
            is NetworkResult.Success -> {
                assertNotNull(result.body.modelUrl)
                assertNotEquals("", result.body.modelUrl)
            }
            else -> fail("network result was not success $result")
        }
    }
}
