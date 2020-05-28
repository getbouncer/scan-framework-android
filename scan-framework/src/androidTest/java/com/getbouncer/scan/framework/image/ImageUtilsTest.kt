package com.getbouncer.scan.framework.image

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.util.Size
import androidx.core.graphics.drawable.toBitmap
import androidx.test.filters.SmallTest
import androidx.test.platform.app.InstrumentationRegistry
import com.getbouncer.scan.framework.test.R
import java.nio.ByteBuffer
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import org.junit.Test

class ImageUtilsTest {

    private val testResources = InstrumentationRegistry.getInstrumentation().context.resources

    @Test
    @SmallTest
    fun bitmap_toRGBByteBuffer_fromPhoto_isCorrect() {
        val bitmap = testResources.getDrawable(R.drawable.ocr_card_numbers_clear, null).toBitmap()
        assertNotNull(bitmap)
        assertEquals(600, bitmap.width, "Bitmap width is not expected")
        assertEquals(375, bitmap.height, "Bitmap height is not expected")

        // convert the bitmap to a byte buffer
        val convertedImage = bitmap.toRGBByteBuffer(mean = 127.5f, std = 128.5f)

        // read in an expected converted file
        val rawStream = testResources.openRawResource(R.raw.ocr_card_numbers_clear)
        val rawBytes = rawStream.readBytes()
        val rawImage = ByteBuffer.wrap(rawBytes)
        rawStream.close()

        // check the size of the files
        assertEquals(rawImage.limit(), convertedImage.limit(), "File size mismatch")
        rawImage.rewind()
        convertedImage.rewind()

        // check each byte of the files
        var encounteredNonZeroByte = false
        while (convertedImage.position() < convertedImage.limit()) {
            val rawImageByte = rawImage.get()
            encounteredNonZeroByte = encounteredNonZeroByte || rawImageByte.toInt() != 0
            assertEquals(rawImageByte, convertedImage.get(), "Difference at byte ${rawImage.position()}")
        }

        assertTrue(encounteredNonZeroByte, "Bytes were all zero")
    }

    @Test
    @SmallTest
    fun bitmap_toRGBByteBuffer_generated_isCorrect() {
        val bitmap = generateSampleBitmap()
        assertNotNull(bitmap)
        assertEquals(100, bitmap.width, "Bitmap width is not expected")
        assertEquals(100, bitmap.height, "Bitmap height is not expected")

        // convert the bitmap to a byte buffer
        val convertedImage = bitmap.toRGBByteBuffer(mean = 127.5f, std = 128.5f)

        // read in an expected converted file
        val rawStream = testResources.openRawResource(R.raw.sample_bitmap)
        val rawBytes = rawStream.readBytes()
        val rawImage = ByteBuffer.wrap(rawBytes)
        rawStream.close()

        // check the size of the files
        assertEquals(rawImage.limit(), convertedImage.limit(), "File size mismatch")
        rawImage.rewind()
        convertedImage.rewind()

        // check each byte of the files
        var encounteredNonZeroByte = false
        while (convertedImage.position() < convertedImage.limit()) {
            val rawImageByte = rawImage.get()
            encounteredNonZeroByte = encounteredNonZeroByte || rawImageByte.toInt() != 0
            assertEquals(rawImageByte, convertedImage.get(), "Difference at byte ${rawImage.position()}")
        }

        assertTrue(encounteredNonZeroByte, "Bytes were all zero")
    }

    @Test
    @SmallTest
    fun bitmap_scale_isCorrect() {
        // read in a sample bitmap file
        val bitmap = testResources.getDrawable(R.drawable.ocr_card_numbers_clear, null).toBitmap()
        assertNotNull(bitmap)
        assertEquals(600, bitmap.width, "Bitmap width is not expected")
        assertEquals(375, bitmap.height, "Bitmap height is not expected")

        // scale the bitmap
        val scaledBitmap = bitmap.scale(Size(bitmap.width / 5, bitmap.height / 5))

        // check the expected sizes of the images
        assertEquals(
            Size(bitmap.width / 5, bitmap.height / 5),
            Size(scaledBitmap.width, scaledBitmap.height),
            "Scaled image is the wrong size"
        )

        // check each pixel of the images
        var encounteredNonZeroPixel = false
        for (x in 0 until scaledBitmap.width) {
            for (y in 0 until scaledBitmap.height) {
                encounteredNonZeroPixel = encounteredNonZeroPixel || scaledBitmap.getPixel(x, y) != 0
            }
        }

        assertTrue(encounteredNonZeroPixel, "Pixels were all zero")
    }

    @Test
    @SmallTest
    fun bitmap_crop_isCorrect() {
        val bitmap = testResources.getDrawable(R.drawable.ocr_card_numbers_clear, null).toBitmap()
        assertNotNull(bitmap)
        assertEquals(600, bitmap.width, "Bitmap width is not expected")
        assertEquals(375, bitmap.height, "Bitmap height is not expected")

        // crop the bitmap
        val croppedBitmap = bitmap.crop(Rect(
            bitmap.width / 4,
            bitmap.height / 4,
            bitmap.width * 3 / 4,
            bitmap.height * 3 / 4
        ))

        // check the expected sizes of the images
        assertEquals(
            Size(bitmap.width * 3 / 4 - bitmap.width / 4, bitmap.height * 3 / 4 - bitmap.height / 4),
            Size(croppedBitmap.width, croppedBitmap.height),
            "Cropped image is the wrong size"
        )

        // check each pixel of the images
        var encounteredNonZeroPixel = false
        for (x in 0 until croppedBitmap.width) {
            for (y in 0 until croppedBitmap.height) {
                val croppedPixel = croppedBitmap.getPixel(x, y)
                val originalPixel = bitmap.getPixel(x + bitmap.width / 4, y + bitmap.height / 4)
                assertEquals(originalPixel, croppedPixel, "Difference at pixel $x, $y")
                encounteredNonZeroPixel = encounteredNonZeroPixel || croppedPixel != 0
            }
        }

        assertTrue(encounteredNonZeroPixel, "Pixels were all zero")
    }

    @Test
    @SmallTest
    fun bitmap_toPngByteArray_isCorrect() {
        val bitmap = testResources.getDrawable(R.drawable.ocr_card_numbers_clear, null).toBitmap()
        assertNotNull(bitmap)
        assertEquals(600, bitmap.width, "Bitmap width is not expected")
        assertEquals(375, bitmap.height, "Bitmap height is not expected")

        // convert the bitmap to a byte array
        val newBitmap = bitmap.toPngByteArray().pngBytesToBitmap()
        assertNotNull(newBitmap)
        assertEquals(bitmap.width, newBitmap.width, "New bitmap width is not expected")
        assertEquals(bitmap.height, newBitmap.height, "New bitmap height is not expected")

        // check each pixel of the images
        var encounteredNonZeroPixel = false
        for (x in 0 until newBitmap.width) {
            for (y in 0 until newBitmap.height) {
                val newPixel = newBitmap.getPixel(x, y)
                val originalPixel = bitmap.getPixel(x, y)
                assertEquals(originalPixel, newPixel, "Difference at pixel $x, $y")
                encounteredNonZeroPixel = encounteredNonZeroPixel || newPixel != 0
            }
        }

        assertTrue(encounteredNonZeroPixel, "Pixels were all zero")
    }

    private fun generateSampleBitmap(size: Size = Size(100, 100)): Bitmap {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        val bitmap = Bitmap.createBitmap(size.width, size.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        for (x in 0 until size.width) {
            for (y in 0 until size.height) {
                val red = 255 * x / size.width
                val green = 255 * y / size.height
                val blue = 255 * x / size.height
                paint.color = Color.rgb(red, green, blue)
                canvas.drawRect(RectF(x.toFloat(), y.toFloat(), x + 1F, y + 1F), paint)
            }
        }

        canvas.drawBitmap(bitmap, 0F, 0F, paint)

        return bitmap
    }
}
