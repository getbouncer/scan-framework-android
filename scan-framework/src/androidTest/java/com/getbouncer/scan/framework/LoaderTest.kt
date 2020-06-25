package com.getbouncer.scan.framework

import android.content.Context
import androidx.test.filters.LargeTest
import androidx.test.filters.SmallTest
import androidx.test.platform.app.InstrumentationRegistry
import com.getbouncer.scan.framework.test.R
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class LoaderTest {
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
    @SmallTest
    fun loadModelFromResource_correct() {
        class ResourceModelLoaderImpl(context: Context) : ResourceLoader(context) {
            override val resource: Int = R.raw.sample_resource
        }

        val byteBuffer = runBlocking { ResourceModelLoaderImpl(testContext).loadData(false) }
        assertNotNull(byteBuffer)
        assertEquals(14, byteBuffer.limit(), "File is not expected size")
        byteBuffer.rewind()

        // ensure not all bytes are zero
        var encounteredNonZeroByte = false
        while (!encounteredNonZeroByte) {
            encounteredNonZeroByte = byteBuffer.get().toInt() != 0
        }
        assertTrue(encounteredNonZeroByte, "All bytes were zero")

        // ensure bytes are correct
        byteBuffer.rewind()
        assertEquals('A', byteBuffer.get().toChar())
        assertEquals('B', byteBuffer.get().toChar())
        assertEquals('C', byteBuffer.get().toChar())
        assertEquals('1', byteBuffer.get().toChar())
    }

    @Test
    @LargeTest
    fun loadModelFromWeb_correct() {
        val localFileName = "test_loadModelFromWeb_correct"
        val localFile = File(testContext.cacheDir, localFileName)
        if (localFile.exists()) {
            localFile.delete()
        }

        class ModelWebLoaderImpl(context: Context) : SignedUrlModelWebLoader(context) {
            override val modelClass = "object_detection"
            override val modelVersion = "v0.0.3"
            override val modelFileName = "ssd.tflite"
            override val hash: String = "7c5a294ff9a1e665f07d3e64d898062e17a2348f01b0be75b2d5295988ce6a4c"
            override val hashAlgorithm = HASH_ALGORITHM
        }

        val byteBuffer = runBlocking { ModelWebLoaderImpl(testContext).loadData(false) }
        assertNotNull(byteBuffer)
        assertEquals(9957868, byteBuffer.limit(), "File is not expected size")
        byteBuffer.rewind()

        // ensure not all bytes are zero
        var encounteredNonZeroByte = false
        while (!encounteredNonZeroByte) {
            encounteredNonZeroByte = byteBuffer.get().toInt() != 0
        }
        assertTrue(encounteredNonZeroByte, "All bytes were zero")

        // ensure bytes 5-8 are "TFL3" ASCII encoded
        byteBuffer.position(4)
        assertEquals(byteBuffer.get().toChar(), 'T')
        assertEquals(byteBuffer.get().toChar(), 'F')
        assertEquals(byteBuffer.get().toChar(), 'L')
        assertEquals(byteBuffer.get().toChar(), '3')
    }

    @Test
    @LargeTest
    fun loadModelFromWeb_fail() {
        val localFileName = "test_loadModelFromWeb_fail"
        val localFile = File(testContext.cacheDir, localFileName)
        if (localFile.exists()) {
            localFile.delete()
        }

        class ModelWebLoaderImpl(context: Context) : SignedUrlModelWebLoader(context) {
            override val modelClass = "invalid_model"
            override val modelVersion = "v0.0.2"
            override val modelFileName = "ssd.tflite"
            override val hash: String = "b7331fd09bf479a20e01b77ebf1b5edbd312639edf8dd883aa7b86f4b7fbfa62"
            override val hashAlgorithm = HASH_ALGORITHM
        }

        assertNull(runBlocking { ModelWebLoaderImpl(testContext).loadData(false) })
    }

    @Test
    @LargeTest
    fun loadModelFromWeb_signedUrlFail() {
        Config.apiKey = "__INTEGRATION_TEST_INVALID_KEY__"
        val localFileName = "test_loadModelFromWeb_fail"
        val localFile = File(testContext.cacheDir, localFileName)
        if (localFile.exists()) {
            localFile.delete()
        }

        class ModelWebLoaderImpl(context: Context) : SignedUrlModelWebLoader(context) {
            override val modelClass = "object_detection"
            override val modelVersion = "v0.0.3"
            override val modelFileName = "ssd.tflite"
            override val hash: String = "7c5a294ff9a1e665f07d3e64d898062e17a2348f01b0be75b2d5295988ce6a4c"
            override val hashAlgorithm = HASH_ALGORITHM
        }

        assertNull(runBlocking { ModelWebLoaderImpl(testContext).loadData(false) })
    }
}
