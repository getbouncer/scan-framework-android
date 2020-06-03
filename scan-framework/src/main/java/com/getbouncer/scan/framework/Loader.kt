package com.getbouncer.scan.framework

import android.content.Context
import android.util.Log
import com.getbouncer.scan.framework.api.NetworkResult
import com.getbouncer.scan.framework.api.dto.BouncerErrorResponse
import com.getbouncer.scan.framework.api.dto.ModelSignedUrlResponse
import com.getbouncer.scan.framework.api.getModelSignedUrl
import com.getbouncer.scan.framework.exception.HashMismatchException
import com.getbouncer.scan.framework.util.retry
import java.io.File
import java.io.FileInputStream
import java.io.FileNotFoundException
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.net.URL
import java.nio.ByteBuffer
import java.nio.channels.FileChannel
import java.security.MessageDigest
import java.security.NoSuchAlgorithmException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

@Throws(IOException::class)
private fun readFileToByteBuffer(
    fileInputStream: FileInputStream,
    startOffset: Long,
    declaredLength: Long
): ByteBuffer = fileInputStream.channel.map(
    FileChannel.MapMode.READ_ONLY,
    startOffset,
    declaredLength
)

/**
 * An interface for loading data into a byte buffer.
 */
interface Loader {
    suspend fun loadData(): ByteBuffer?
}

/**
 * A factory for creating [ByteBuffer] objects from an android resource.
 */
abstract class ResourceLoader(private val context: Context) : Loader {

    protected abstract val resource: Int

    override suspend fun loadData(): ByteBuffer? = withContext(Dispatchers.IO) {
        Stats.trackRepeatingTask("resource_loader:$resource") {
            try {
                context.resources.openRawResourceFd(resource).use { fileDescriptor ->
                    FileInputStream(fileDescriptor.fileDescriptor).use { input ->
                        val data = readFileToByteBuffer(
                            input,
                            fileDescriptor.startOffset,
                            fileDescriptor.declaredLength
                        )
                        data
                    }
                }
            } catch (t: Throwable) {
                Log.e(Config.logTag, "Failed to load resource", t)
                null
            }
        }
    }
}

/**
 * A factory for creating [ByteBuffer] objects from files downloaded from the web.
 */
abstract class WebLoader(private val context: Context) : Loader {

    private val loadDataMutex = Mutex()
    private var loadException: Throwable? = null

    abstract val url: URL
    abstract val hash: String

    private val localFileName: String by lazy { url.path.replace('/', '_') }

    companion object {
        private const val HASH_ALGORITHM = "SHA-256"
    }

    /**
     * Download, verify, and read data from the web.
     */
    override suspend fun loadData(): ByteBuffer? = loadDataMutex.withLock {
        val stat = Stats.trackRepeatingTask("web_loader:$localFileName")

        val loadException = this@WebLoader.loadException
        if (loadException != null) {
            stat.trackResult(loadException::class.java.simpleName)
            return null
        }

        val exception = try {
            retry(NetworkConfig.retryDelay) {
                try {
                    downloadAndVerify()
                    null
                } catch (t: FileNotFoundException) {
                    // do not retry FileNotFoundExceptions
                    t
                }
            }
        } catch (t: Throwable) {
            t
        }

        if (exception != null) {
            this@WebLoader.loadException = exception
            stat.trackResult(exception::class.java.simpleName)
            Log.e(Config.logTag, "Failed to get signed url for model", exception)
            return null
        }

        stat.trackResult("success")
        readFileToByteBuffer(localFileName)
    }

    /**
     * Download and verify the hash of a file.
     */
    @Throws(IOException::class, HashMismatchException::class, NoSuchAlgorithmException::class, FileNotFoundException::class)
    private suspend fun downloadAndVerify() {
        if (!hashMatches(localFileName, hash)) {
            downloadFile(url, localFileName)
            if (!hashMatches(localFileName, hash)) {
                throw HashMismatchException(
                    HASH_ALGORITHM,
                    hash,
                    calculateHash(localFileName)
                )
            }
        }
    }

    @Throws(IOException::class, NoSuchAlgorithmException::class)
    private suspend fun hashMatches(localFileName: String, hash: String) =
        hash == calculateHash(localFileName)

    @Throws(IOException::class, NoSuchAlgorithmException::class)
    private suspend fun calculateHash(localFileName: String): String? =
        withContext(Dispatchers.IO) {
            val file = File(context.cacheDir, localFileName)
            if (file.exists()) {
                val digest = MessageDigest.getInstance(HASH_ALGORITHM)
                FileInputStream(file).use { digest.update(it.readBytes()) }
                digest.digest().joinToString("") { "%02x".format(it) }
            } else {
                null
            }
        }

    private suspend fun readFileToByteBuffer(localFileName: String): ByteBuffer =
        withContext(Dispatchers.IO) {
            val file = File(context.cacheDir, localFileName)
            FileInputStream(file).use {
                readFileToByteBuffer(
                    it,
                    0,
                    file.length()
                )
            }
        }

    @Throws(IOException::class)
    private suspend fun downloadFile(url: URL, localFileName: String) = withContext(Dispatchers.IO) {
        val urlConnection = url.openConnection()
        val outputFile = File(context.cacheDir, localFileName)

        if (outputFile.exists()) {
            if (!outputFile.delete()) {
                return@withContext
            }
        }

        if (!outputFile.createNewFile()) {
            return@withContext
        }

        urlConnection.getInputStream().use {
            readStreamToFile(it, outputFile)
        }
    }

    @Throws(IOException::class)
    private fun readStreamToFile(stream: InputStream, file: File) =
        FileOutputStream(file).use { it.write(stream.readBytes()) }
}

/**
 * A factory for creating [ByteBuffer] objects for models.
 */
abstract class ModelWebLoader(context: Context) : WebLoader(context) {

    abstract val modelClass: String
    abstract val modelVersion: String
    abstract val modelFileName: String

    override val url: URL by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        when (val signedUrlResponse = runBlocking {
            getModelSignedUrl(modelClass, modelVersion, modelFileName)
        }) {
            is NetworkResult.Success<ModelSignedUrlResponse, BouncerErrorResponse> ->
                URL(signedUrlResponse.body.modelUrl)
            else -> {
                URL("${NetworkConfig.baseUrl}/v1/signed_url_failure/model/$modelClass/$modelVersion/android/$modelFileName")
            }
        }
    }
}
