package com.getbouncer.scan.framework.api

import androidx.test.filters.LargeTest
import com.getbouncer.scan.framework.Config
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.test.fail
import kotlinx.coroutines.runBlocking
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
    fun validateApiKey() {
        when (val result = runBlocking { com.getbouncer.scan.framework.api.validateApiKey() }) {
            is NetworkResult.Success -> {
                assertTrue(result.body.isApiKeyValid)
                assertNull(result.body.keyInvalidReason)
            }
            else -> fail("Network result was not success $result")
        }
    }

    @Test
    @LargeTest
    fun getModelSignedUrl() {
        when (val result = runBlocking {
            getModelSignedUrl(
                "fake_model",
                "v0.0.1",
                "model.tflite"
            )
        }) {
            is NetworkResult.Success -> {
                assertNotNull(result.body.modelUrl)
                assertNotEquals("", result.body.modelUrl)
            }
            else -> fail("network result was not success $result")
        }
    }
}
