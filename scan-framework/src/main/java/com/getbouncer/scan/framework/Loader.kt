package com.getbouncer.scan.framework

import android.content.Context
import android.util.Log
import androidx.annotation.VisibleForTesting
import com.getbouncer.scan.framework.api.NetworkResult
import com.getbouncer.scan.framework.api.getModelSignedUrl
import com.getbouncer.scan.framework.api.getModelUpgradePath
import com.getbouncer.scan.framework.util.retry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.FileNotFoundException
import java.io.FileOutputStream
import java.io.IOException
import java.lang.Exception
import java.net.URL
import java.nio.ByteBuffer
import java.nio.channels.FileChannel
import java.security.MessageDigest
import java.security.NoSuchAlgorithmException

const val HASH_ALGORITHM = "SHA-256"

/**
 * An interface for loading data into a byte buffer.
 */
interface Loader {

    /**
     * Load data into memory. If this is part of the critical path (i.e. the loaded model will be used immediately),
     * the loader will prioritize loading the model over getting the latest version.
     */
    suspend fun loadData(criticalPath: Boolean): ByteBuffer?
}

/**
 * A factory for creating [ByteBuffer] objects from an android resource.
 */
abstract class ResourceLoader(private val context: Context) : Loader {

    protected abstract val resource: Int

    override suspend fun loadData(criticalPath: Boolean): ByteBuffer? = withContext(Dispatchers.IO) {
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

sealed class WebLoader : Loader {

    protected data class DownloadDetails(val url: URL, val hash: String, val hashAlgorithm: String)

    private val loadDataMutex = Mutex()

    /**
     * Keep track of any load exceptions that occurred after the specified number of retries. This is used to prevent
     * the loader from repeatedly trying to load the model from multiple threads after the number of retries has been
     * reached.
     */
    private var loadException: Throwable? = null

    override suspend fun loadData(criticalPath: Boolean): ByteBuffer? = loadDataMutex.withLock {
        val stat = Stats.trackRepeatingTask("web_loader")

        loadException?.run {
            stat.trackResult(this::class.java.simpleName)
            return@withLock null
        }

        // attempt to load the model from local cache
        tryLoadCachedModel(criticalPath)?.let {
            stat.trackResult("success")
            return@withLock it
        }

        // get details for downloading the model
        val downloadDetails = getDownloadDetails()
        if (downloadDetails == null) {
            stat.trackResult("download_details_failure")
            return null
        }

        // check the local cache for a matching model
        tryLoadCachedModel(downloadDetails.hash, downloadDetails.hashAlgorithm)?.let {
            stat.trackResult("success")
            return@withLock it
        }

        // download the model
        val downloadedFile = try {
            downloadAndVerify(downloadDetails.url, getDownloadOutputFile(), downloadDetails.hash, downloadDetails.hashAlgorithm)
        } catch (t: Throwable) {
            loadException = t
            stat.trackResult(t::class.java.simpleName)
            return null
        }

        cleanUpPostDownload(downloadedFile)

        stat.trackResult("success")
        readFileToByteBuffer(downloadedFile)
    }

    /**
     * Attempt to load the model from the local cache.
     */
    protected abstract suspend fun tryLoadCachedModel(criticalPath: Boolean): ByteBuffer?

    /**
     * Attempt to load a cached model given the required [hash] and [hashAlgorithm].
     */
    protected abstract suspend fun tryLoadCachedModel(hash: String, hashAlgorithm: String): ByteBuffer?

    /**
     * Get the file where the model should be downloaded.
     */
    protected abstract suspend fun getDownloadOutputFile(): File

    /**
     * Get [DownloadDetails] for the model that will be downloaded.
     */
    protected abstract suspend fun getDownloadDetails(): DownloadDetails?

    /**
     * After download, clean up.
     */
    protected abstract suspend fun cleanUpPostDownload(downloadedFile: File)

    /**
     * Clear the cache for this loader. This will force new downloads.
     */
    abstract suspend fun clearCache()
}

/**
 * A loader that directly downloads a model and loads it into memory
 */
abstract class DirectDownloadWebLoader(private val context: Context) : WebLoader() {
    abstract val url: URL
    abstract val hash: String
    abstract val hashAlgorithm: String

    private val localFileName: String by lazy { url.path.replace('/', '_') }

    override suspend fun tryLoadCachedModel(criticalPath: Boolean): ByteBuffer? {
        val localFile = getDownloadOutputFile()
        return if (isLocalFileValid(localFile, hash, hashAlgorithm)) {
            readFileToByteBuffer(localFile)
        } else {
            null
        }
    }

    override suspend fun tryLoadCachedModel(hash: String, hashAlgorithm: String): ByteBuffer? = null

    override suspend fun getDownloadOutputFile() = File(context.cacheDir, localFileName)

    override suspend fun getDownloadDetails(): DownloadDetails? = DownloadDetails(url, hash, hashAlgorithm)

    override suspend fun cleanUpPostDownload(downloadedFile: File) { /* nothing to do */ }

    override suspend fun clearCache() {
        val localFile = getDownloadOutputFile()
        if (localFile.exists()) {
            localFile.delete()
        }
    }
}

/**
 * A loader that uses the signed URL server endpoints to download a model and load it into memory
 */
abstract class SignedUrlModelWebLoader(private val context: Context) : DirectDownloadWebLoader(context) {
    abstract val modelClass: String
    abstract val modelVersion: String
    abstract val modelFileName: String

    private val localFileName by lazy { "${modelClass}_${modelFileName}_$modelVersion" }

    // this field is not used by this class
    override val url: URL = URL("https://getbouncer.com")

    override suspend fun getDownloadDetails() =
        when (val signedUrlResponse = getModelSignedUrl(context, modelClass, modelVersion, modelFileName)) {
            is NetworkResult.Success ->
                try {
                    URL(signedUrlResponse.body.modelUrl)
                } catch (t: Throwable) {
                    Log.e(Config.logTag, "Invalid signed url for model $modelClass: ${signedUrlResponse.body.modelUrl}")
                    null
                }
            else -> {
                Log.e(Config.logTag, "Failed to get signed url for model $modelClass: ${signedUrlResponse.responseCode}")
                null
            }
        }?.let { DownloadDetails(it, hash, hashAlgorithm) }
}

/**
 * A loader that queries Bouncer servers for updated models. If a new version is found, download it. If the model
 * details match what is cached, return those instead.
 */
abstract class UpdatingModelWebLoader(private val context: Context) : SignedUrlModelWebLoader(context) {
    abstract val modelFrameworkVersion: String

    abstract val defaultModelVersion: String
    abstract val defaultModelFileName: String
    abstract val defaultModelHash: String

    private var cachedUrl: URL? = null
    private var cachedHash: String? = null

    private val cacheFolder by lazy { ensureLocalFolder("${modelClass}_$modelFrameworkVersion") }

    override val modelVersion: String by lazy { defaultModelVersion }
    override val modelFileName: String by lazy { defaultModelFileName }
    override val hash: String by lazy { defaultModelHash }
    override val hashAlgorithm: String = HASH_ALGORITHM

    /**
     * If this model is needed immediately, try to read from the local cache before performing an upgrade
     */
    override suspend fun tryLoadCachedModel(criticalPath: Boolean): ByteBuffer? =
        if (criticalPath) {
            getLatestFile()?.let { readFileToByteBuffer(it) }
        } else {
            null
        }

    /**
     * If the latest model has already been downloaded, load it into memory.
     */
    override suspend fun tryLoadCachedModel(hash: String, hashAlgorithm: String): ByteBuffer? =
        getMatchingFile(hash, hashAlgorithm)?.let { readFileToByteBuffer(it) }

    override suspend fun getDownloadOutputFile() = File(cacheFolder, System.currentTimeMillis().toString())

    override suspend fun getDownloadDetails(): DownloadDetails? {
        val url = cachedUrl
        val hash = cachedHash
        if (url != null && !hash.isNullOrEmpty()) {
            return DownloadDetails(url, hash, HASH_ALGORITHM)
        }

        return when (val modelUpgradeResponse = getModelUpgradePath(context, modelClass, modelFrameworkVersion)) {
            is NetworkResult.Success ->
                try {
                    DownloadDetails(URL(modelUpgradeResponse.body.modelUrl), modelUpgradeResponse.body.sha256, HASH_ALGORITHM).apply {
                        cachedUrl = url
                        cachedHash = hash
                    }
                } catch (t: Throwable) {
                    Log.e(Config.logTag, "Invalid signed url for model $modelClass: ${modelUpgradeResponse.body.modelUrl}")
                    null
                }
            else -> {
                Log.e(Config.logTag, "Failed to get latest details for model $modelClass: ${modelUpgradeResponse.responseCode}")
                super.getDownloadDetails()?.apply {
                    cachedUrl = url
                    cachedHash = hash
                }
            }
        }
    }

    /**
     * Delete all files in cache that are not the recently downloaded file.
     */
    override suspend fun cleanUpPostDownload(downloadedFile: File) = withContext(Dispatchers.IO) {
        cacheFolder.listFiles()?.filter { it != downloadedFile }?.forEach { it.delete() }.let { Unit }
    }

    /**
     * If a file in the cache directory matches the provided [hash], return it.
     */
    private suspend fun getMatchingFile(hash: String, hashAlgorithm: String): File? =
        cacheFolder.listFiles()?.sortedByDescending { it.lastModified() }?.firstOrNull { calculateHash(it, hashAlgorithm) == hash }

    /**
     * Get the most recently created file in the cache folder. Return null if no files in this
     */
    private fun getLatestFile() = cacheFolder.listFiles()?.maxBy { it.lastModified() }

    /**
     * Ensure that the local folder exists and get it.
     */
    private fun ensureLocalFolder(folderName: String): File {
        val localFolder = File(context.cacheDir, folderName)
        if (localFolder.exists() && !localFolder.isDirectory) {
            localFolder.delete()
        }
        if (!localFolder.exists()) {
            localFolder.mkdir()
        }
        return localFolder
    }

    /**
     * Force re-download of models by clearing the cache.
     */
    override suspend fun clearCache() {
        cacheFolder.deleteRecursively()
    }
}

/**
 * Read a [file] into a [ByteBuffer].
 */
private suspend fun readFileToByteBuffer(file: File) = withContext(Dispatchers.IO) {
    FileInputStream(file).use {
        readFileToByteBuffer(it, 0, file.length())
    }
}

/**
 * Read a [fileInputStream] into a [ByteBuffer].
 */
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
 * Determine if a local file is valid and matches the expected hash
 */
private suspend fun isLocalFileValid(localFile: File, hash: String, hashAlgorithm: String) = try {
    hash == calculateHash(localFile, hashAlgorithm)
} catch (t: Throwable) {
    false
}

/**
 * Download a file from a given [url] and ensure that it matches the expected [hash]
 */
@Throws(IOException::class, FileCreationException::class, NoSuchAlgorithmException::class, HashMismatchException::class)
private suspend fun downloadAndVerify(
    url: URL,
    outputFile: File,
    hash: String,
    hashAlgorithm: String
): File {
    val downloadedFile = downloadFile(url, outputFile)
    val calculatedHash = calculateHash(downloadedFile, hashAlgorithm)

    if (hash != calculatedHash) {
        downloadedFile.delete()
        throw HashMismatchException(hashAlgorithm, hash, calculatedHash)
    }

    return downloadedFile
}

/**
 * Calculate the hash of a file using the [hashAlgorithm].
 */
@Throws(IOException::class, NoSuchAlgorithmException::class)
private suspend fun calculateHash(file: File, hashAlgorithm: String): String? = withContext(Dispatchers.IO) {
    if (file.exists()) {
        val digest = MessageDigest.getInstance(hashAlgorithm)
        FileInputStream(file).use { digest.update(it.readBytes()) }
        digest.digest().joinToString("") { "%02x".format(it) }
    } else {
        null
    }
}

/**
 * Download a file from the provided [url] into the provided [outputFile].
 */
@Throws(IOException::class, FileCreationException::class)
private suspend fun downloadFile(url: URL, outputFile: File) = withContext(Dispatchers.IO) {
    retry(NetworkConfig.retryDelay, excluding = listOf(FileNotFoundException::class.java)) {
        val urlConnection = url.openConnection()

        if (outputFile.exists()) {
            outputFile.delete()
        }

        if (!outputFile.createNewFile()) {
            throw FileCreationException(outputFile.name)
        }

        urlConnection.getInputStream().use { stream ->
            FileOutputStream(outputFile).use { it.write(stream.readBytes()) }
        }

        outputFile
    }
}

class HashMismatchException(val algorithm: String, val expected: String, val actual: String?) :
    Exception("Invalid hash result for algorithm '$algorithm'. Expected '$expected' but got '$actual'") {
    override fun toString() = "HashMismatchException(algorithm='$algorithm', expected='$expected', actual='$actual')"
}

class FileCreationException(val fileName: String) : Exception("Unable to create local file '$fileName'") {
    override fun toString() = "FileCreationException(fileName='$fileName')"
}
