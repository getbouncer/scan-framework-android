package com.getbouncer.scan.framework

import android.content.Context
import androidx.test.filters.LargeTest
import androidx.test.filters.SmallTest
import androidx.test.platform.app.InstrumentationRegistry
import com.getbouncer.scan.framework.test.R
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test

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
            override val resource: Int = R.drawable.ocr_card_numbers_clear
        }

        val byteBuffer = runBlocking { ResourceModelLoaderImpl(testContext).loadData() }
        assertNotNull(byteBuffer)
        assertEquals(335417, byteBuffer.limit(), "File is not expected size")
        byteBuffer.rewind()

        // ensure not all bytes are zero
        var encounteredNonZeroByte = false
        while (!encounteredNonZeroByte) {
            encounteredNonZeroByte = byteBuffer.get().toInt() != 0
        }
        assertTrue(encounteredNonZeroByte, "All bytes were zero")

        // ensure bytes 5-8 are "TFL3" ASCII encoded
        byteBuffer.position(4)
        assertEquals(byteBuffer.get().toInt(), 13)
        assertEquals(byteBuffer.get().toInt(), 10)
        assertEquals(byteBuffer.get().toInt(), 26)
        assertEquals(byteBuffer.get().toInt(), 10)
    }

    @Test
    @SmallTest
    fun loadModelFromWeb_correct() {
        val localFileName = "test_loadModelFromWeb_correct"
        val localFile = File(testContext.cacheDir, localFileName)
        if (localFile.exists()) {
            localFile.delete()
        }

        class ModelWebLoaderImpl(context: Context) : ModelWebLoader(context) {
            override val modelClass = "object_detection"
            override val modelVersion = "v0.0.3"
            override val modelFileName = "ssd.tflite"
            override val hash: String = "7c5a294ff9a1e665f07d3e64d898062e17a2348f01b0be75b2d5295988ce6a4c"
        }

        val byteBuffer = runBlocking { ModelWebLoaderImpl(testContext).loadData() }
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

        class ModelWebLoaderImpl(context: Context) : ModelWebLoader(context) {
            override val modelClass = "invalid_model"
            override val modelVersion = "v0.0.2"
            override val modelFileName = "ssd.tflite"
            override val hash: String = "b7331fd09bf479a20e01b77ebf1b5edbd312639edf8dd883aa7b86f4b7fbfa62"
        }

        assertNull(runBlocking { ModelWebLoaderImpl(testContext).loadData() })
    }

    @Test
    @LargeTest
    fun loadModelFromWeb_signedUrlFail() {
        Config.apiKey = "4U7hWrEBdmgZrrIOQanpzJTaiwlZPFhf"
        val localFileName = "test_loadModelFromWeb_fail"
        val localFile = File(testContext.cacheDir, localFileName)
        if (localFile.exists()) {
            localFile.delete()
        }

        class ModelWebLoaderImpl(context: Context) : ModelWebLoader(context) {
            override val modelClass = "object_detection"
            override val modelVersion = "v0.0.3"
            override val modelFileName = "ssd.tflite"
            override val hash: String = "7c5a294ff9a1e665f07d3e64d898062e17a2348f01b0be75b2d5295988ce6a4c"
        }

        assertNull(runBlocking { ModelWebLoaderImpl(testContext).loadData() })
    }
}
