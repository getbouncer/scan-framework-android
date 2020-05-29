package com.getbouncer.scan.framework.image

import android.app.ActivityManager
import android.content.Context
import android.content.pm.ConfigurationInfo
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageFormat
import android.graphics.Matrix
import android.graphics.Rect
import android.graphics.YuvImage
import android.media.Image
import android.util.Size
import com.getbouncer.scan.framework.exception.ImageTypeNotSupportedException
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.IntBuffer
import kotlin.math.roundToInt

private const val DIM_PIXEL_SIZE = 3
private const val NUM_BYTES_PER_CHANNEL = 4 // Float.size / Byte.size

data class ImageTransformValues(val red: Float, val green: Float, val blue: Float)

fun Bitmap.toRGBByteBuffer(mean: Float = 0F, std: Float = 255F): ByteBuffer =
    this.toRGBByteBuffer(ImageTransformValues(mean, mean, mean), ImageTransformValues(std, std, std))

/**
 * Determine if this application supports an image format.
 */
fun Image.isSupportedFormat() = isSupportedFormat(this.format)

/**
 * Determine if this application supports an image format.
 */
fun isSupportedFormat(imageFormat: Int) = when (imageFormat) {
    ImageFormat.YUV_420_888, ImageFormat.JPEG -> true
    ImageFormat.NV21 -> false // this fails on older devices
    else -> false
}

/**
 * Convert an image to a bitmap for processing. This will throw an [ImageTypeNotSupportedException]
 * if the image type is not supported (see [isSupportedFormat]).
 */
fun Image.toBitmap(
    crop: Rect = Rect(
        0,
        0,
        this.width,
        this.height
    ),
    quality: Int = 75
): Bitmap = when (this.format) {
    ImageFormat.NV21 -> planes[0].buffer.toByteArray().nv21ToYuv(width, height).toBitmap(crop, quality)
    ImageFormat.YUV_420_888 -> yuvToNV21Bytes().nv21ToYuv(width, height).toBitmap(crop, quality)
    ImageFormat.JPEG -> jpegToBitmap().crop(crop)
    else -> throw ImageTypeNotSupportedException(this.format)
}

/**
 * Convert a YuvImage to a bitmap.
 */
fun YuvImage.toBitmap(
    crop: Rect = Rect(
        0,
        0,
        this.width,
        this.height
    ),
    quality: Int = 75
): Bitmap {
    val out = ByteArrayOutputStream()
    compressToJpeg(crop, quality, out)

    val imageBytes = out.toByteArray()
    return BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)
}

/**
 * Convert an NV21 byte array to a YuvImage.
 */
fun ByteArray.nv21ToYuv(width: Int, height: Int) = YuvImage(
    this,
    ImageFormat.NV21,
    width,
    height,
    null
)

/**
 * From https://stackoverflow.com/questions/52726002/camera2-captured-picture-conversion-from-yuv-420-888-to-nv21/52740776#52740776
 *
 * https://stackoverflow.com/questions/32276522/convert-nv21-byte-array-into-bitmap-readable-format
 */
private fun Image.yuvToNV21Bytes(): ByteArray {
    val crop = this.cropRect
    val format = this.format
    val width = crop.width()
    val height = crop.height()
    val planes = this.planes
    val nv21Bytes = ByteArray(width * height * ImageFormat.getBitsPerPixel(format) / 8)
    val rowData = ByteArray(planes[0].rowStride)

    var channelOffset = 0
    var outputStride = 1

    for (i in planes.indices) {
        when (i) {
            0 -> {
                channelOffset = 0
                outputStride = 1
            }
            1 -> {
                channelOffset = width * height + 1
                outputStride = 2
            }
            2 -> {
                channelOffset = width * height
                outputStride = 2
            }
        }

        val buffer = planes[i].buffer
        val rowStride = planes[i].rowStride
        val pixelStride = planes[i].pixelStride
        val shift = if (i == 0) 0 else 1
        val w = width shr shift
        val h = height shr shift

        buffer.position(rowStride * (crop.top shr shift) + pixelStride * (crop.left shr shift))

        for (row in 0 until h) {
            var length: Int

            if (pixelStride == 1 && outputStride == 1) {
                length = w
                buffer.get(nv21Bytes, channelOffset, length)
                channelOffset += length
            } else {
                length = (w - 1) * pixelStride + 1
                buffer.get(rowData, 0, length)
                for (col in 0 until w) {
                    nv21Bytes[channelOffset] = rowData[col * pixelStride]
                    channelOffset += outputStride
                }
            }

            if (row < h - 1) {
                buffer.position(buffer.position() + rowStride - length)
            }
        }
    }

    return nv21Bytes
}

private fun Image.jpegToBitmap(): Bitmap {
    check(format == ImageFormat.JPEG) { "Image is not in JPEG format" }

    val imageBuffer = planes[0].buffer
    val imageBytes = ByteArray(imageBuffer.remaining())
    imageBuffer.get(imageBytes)
    return BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)
}

private fun ByteBuffer.toByteArray(): ByteArray {
    val bytes = ByteArray(remaining())
    get(bytes)
    return bytes
}

fun Bitmap.toRGBByteBuffer(mean: ImageTransformValues, std: ImageTransformValues): ByteBuffer {
    val argb = IntArray(width * height).also {
        getPixels(it, 0, width, 0, 0, width, height)
    }

    val rgbFloat = ByteBuffer.allocateDirect(
        this.width * this.height * DIM_PIXEL_SIZE * NUM_BYTES_PER_CHANNEL)
    rgbFloat.order(ByteOrder.nativeOrder())
    argb.forEach {
        // ignore the alpha value ((it shr 24 and 0xFF) - mean.alpha) / std.alpha)
        rgbFloat.putFloat(((it shr 16 and 0xFF) - mean.red) / std.red)
        rgbFloat.putFloat(((it shr 8 and 0xFF) - mean.green) / std.green)
        rgbFloat.putFloat(((it and 0xFF) - mean.blue) / std.blue)
    }

    rgbFloat.rewind()
    return rgbFloat
}

fun Bitmap.toPngByteArray(): ByteArray = ByteArrayOutputStream().also {
    this.compress(Bitmap.CompressFormat.PNG, 100, it)
}.toByteArray()

fun ByteArray.pngBytesToBitmap(): Bitmap? = BitmapFactory.decodeByteArray(this, 0, this.size)

fun hasOpenGl31(context: Context): Boolean {
    val openGlVersion = 0x00030001
    val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
    val configInfo = activityManager.deviceConfigurationInfo

    return if (configInfo.reqGlEsVersion != ConfigurationInfo.GL_ES_VERSION_UNDEFINED) {
        configInfo.reqGlEsVersion >= openGlVersion
    } else {
        false
    }
}

fun ByteBuffer.rbgaToBitmap(size: Size, mean: Float = 0F, std: Float = 255F): Bitmap {
    this.rewind()
    check(this.limit() == size.width * size.height) { "ByteBuffer limit does not match expected size" }
    val bitmap = Bitmap.createBitmap(size.width, size.height, Bitmap.Config.ARGB_8888)
    val rgba = IntBuffer.allocate(size.width * size.height)
    while (this.hasRemaining()) {
        rgba.put(
            (0xFF shl 24) + // set 0xFF for the alpha value
            (((this.float * std) + mean).roundToInt()) +
            (((this.float * std) + mean).roundToInt() shl 8) +
            (((this.float * std) + mean).roundToInt() shl 16)
        )
    }
    rgba.rewind()
    bitmap.copyPixelsFromBuffer(rgba)
    return bitmap
}

fun Bitmap.crop(crop: Rect): Bitmap {
    require(crop.left < crop.right && crop.top < crop.bottom) { "Cannot use negative crop" }
    require(crop.left >= 0 && crop.top >= 0 && crop.bottom <= this.height && crop.right <= this.width) { "Crop is larger than source image" }
    return Bitmap.createBitmap(
        this,
        crop.left,
        crop.top,
        crop.width(),
        crop.height()
    )
}

fun Bitmap.rotate(rotationDegrees: Float): Bitmap = if (rotationDegrees != 0F) {
    val matrix = Matrix()
    matrix.postRotate(rotationDegrees)
    Bitmap.createBitmap(this, 0, 0, this.width, this.height, matrix, true)
} else {
    this
}

fun Bitmap.scale(size: Size, filter: Boolean = false): Bitmap =
    if (size.width == width && size.height == height) {
        this
    } else {
        Bitmap.createScaledBitmap(this, size.width, size.height, filter)
    }

fun Bitmap.scale(percentage: Float, filter: Boolean = false): Bitmap =
    if (percentage == 1F) {
        this
    } else {
        Bitmap.createScaledBitmap(this, (width * percentage).toInt(), (height * percentage).toInt(), filter)
    }

fun Bitmap.size(): Size = Size(this.width, this.height)
