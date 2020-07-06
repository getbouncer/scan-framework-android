package com.getbouncer.scan.framework

import android.content.Context
import android.util.Log
import com.getbouncer.scan.framework.api.NetworkResult
import com.getbouncer.scan.framework.api.getModelSignedUrl
import com.getbouncer.scan.framework.api.getModelUpgradePath
import com.getbouncer.scan.framework.ml.trackModelLoaded
import com.getbouncer.scan.framework.time.asEpochMillisecondsClockMark
import com.getbouncer.scan.framework.time.weeks
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

private val CACHE_MODEL_TIME = 1.weeks
private const val CACHE_MODEL_MAX_COUNT = 3

data class LoadedModelMeta(
    val modelVersion: String,
    val model: ByteBuffer?
)

/**
 * An interface for loading data into a byte buffer.
 */
interface Loader {
    val modelClass: String
    val modelFrameworkVersion: Int

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
    protected abstract val modelVersion: String
    protected abstract val resource: Int

    override suspend fun loadData(criticalPath: Boolean): ByteBuffer? = withContext(Dispatchers.IO) {
        Stats.trackRepeatingTask("resource_loader:$resource") {
            try {
                val model = context.resources.openRawResourceFd(resource).use { fileDescriptor ->
                    FileInputStream(fileDescriptor.fileDescriptor).use { input ->
                        val data = readFileToByteBuffer(
                            input,
                            fileDescriptor.startOffset,
                            fileDescriptor.declaredLength
                        )
                        data
                    }
                }
                trackModelLoaded(modelClass, modelVersion, modelFrameworkVersion, true)
                model
            } catch (t: Throwable) {
                Log.e(Config.logTag, "Failed to load resource", t)
                trackModelLoaded(modelClass, modelVersion, modelFrameworkVersion, false)
                null
            }
        }
    }
}

sealed class WebLoader : Loader {
    protected data class DownloadDetails(val url: URL, val hash: String, val hashAlgorithm: String, val modelVersion: String)

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
            val meta = tryLoadCachedModel(true)
            trackModelLoaded(modelClass, meta.modelVersion, modelFrameworkVersion, meta.model != null)
            if (meta.model == null) {
                stat.trackResult(this::class.java.simpleName)
            } else {
                stat.trackResult("success")
            }
            return@withLock meta.model
        }

        // attempt to load the model from local cache
        tryLoadCachedModel(criticalPath).run {
            model?.let {
                stat.trackResult("success")
                trackModelLoaded(modelClass, modelVersion, modelFrameworkVersion, true)
                return@withLock it
            }
        }

        // get details for downloading the model
        val downloadDetails = getDownloadDetails() ?: run {
            stat.trackResult("download_details_failure")
            val meta = tryLoadCachedModel(true)
            trackModelLoaded(modelClass, meta.modelVersion, modelFrameworkVersion, meta.model != null)
            return@withLock meta.model
        }

        // check the local cache for a matching model
        tryLoadCachedModel(downloadDetails.hash, downloadDetails.hashAlgorithm).run {
            model?.let {
                stat.trackResult("success")
                trackModelLoaded(modelClass, modelVersion, modelFrameworkVersion, true)
                return@withLock it
            }
        }

        // download the model
        val downloadedFile = try {
            downloadAndVerify(downloadDetails.url, getDownloadOutputFile(downloadDetails.modelVersion), downloadDetails.hash, downloadDetails.hashAlgorithm)
        } catch (t: Throwable) {
            loadException = t
            val meta = tryLoadCachedModel(true)
            trackModelLoaded(modelClass, meta.modelVersion, modelFrameworkVersion, meta.model != null)
            if (meta.model == null) {
                stat.trackResult(t::class.java.simpleName)
            } else {
                stat.trackResult("success")
            }
            return meta.model
        }

        cleanUpPostDownload(downloadedFile)

        stat.trackResult("success")
        trackModelLoaded(modelClass, downloadDetails.modelVersion, modelFrameworkVersion, true)
        readFileToByteBuffer(downloadedFile)
    }

    /**
     * Attempt to load the model from the local cache.
     */
    protected abstract suspend fun tryLoadCachedModel(criticalPath: Boolean): LoadedModelMeta

    /**
     * Attempt to load a cached model given the required [hash] and [hashAlgorithm].
     */
    protected abstract suspend fun tryLoadCachedModel(hash: String, hashAlgorithm: String): LoadedModelMeta

    /**
     * Get the file where the model should be downloaded.
     */
    protected abstract suspend fun getDownloadOutputFile(modelVersion: String): File

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
    abstract val modelVersion: String

    private val localFileName: String by lazy { url.path.replace('/', '_') }

    override suspend fun tryLoadCachedModel(criticalPath: Boolean): LoadedModelMeta {
        val localFile = getDownloadOutputFile(modelVersion)
        return if (isLocalFileValid(localFile, hash, hashAlgorithm)) {
            LoadedModelMeta(modelVersion, readFileToByteBuffer(localFile))
        } else {
            LoadedModelMeta(modelVersion, null)
        }
    }

    override suspend fun tryLoadCachedModel(hash: String, hashAlgorithm: String) = LoadedModelMeta("", null)

    override suspend fun getDownloadOutputFile(modelVersion: String) = File(context.cacheDir, localFileName)

    override suspend fun getDownloadDetails(): DownloadDetails? = DownloadDetails(url, hash, hashAlgorithm, modelVersion)

    override suspend fun cleanUpPostDownload(downloadedFile: File) { /* nothing to do */ }

    override suspend fun clearCache() {
        val localFile = getDownloadOutputFile(modelVersion)
        if (localFile.exists()) {
            localFile.delete()
        }
    }
}

/**
 * A loader that uses the signed URL server endpoints to download a model and load it into memory
 */
abstract class SignedUrlModelWebLoader(private val context: Context) : DirectDownloadWebLoader(context) {
    abstract val modelFileName: String

    private val localFileName by lazy { "${modelClass}_${modelFileName}_$modelVersion" }

    // this field is not used by this class
    override val url: URL = URL("https://getbouncer.com")

    override suspend fun getDownloadOutputFile(modelVersion: String) = File(context.cacheDir, localFileName)

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
        }?.let { DownloadDetails(it, hash, hashAlgorithm, modelVersion) }
}

/**
 * A loader that queries Bouncer servers for updated models. If a new version is found, download it. If the model
 * details match what is cached, return those instead.
 */
abstract class UpdatingModelWebLoader(private val context: Context) : SignedUrlModelWebLoader(context) {
    abstract val defaultModelVersion: String
    abstract val defaultModelFileName: String
    abstract val defaultModelHash: String
    abstract val defaultModelHashAlgorithm: String

    private var cachedDownloadDetails: DownloadDetails? = null

    private val cacheFolder by lazy { ensureLocalFolder("${modelClass}_$modelFrameworkVersion") }

    override val modelVersion: String by lazy { defaultModelVersion }
    override val modelFileName: String by lazy { defaultModelFileName }
    override val hash: String by lazy { defaultModelHash }
    override val hashAlgorithm: String by lazy { defaultModelHashAlgorithm }

    /**
     * If this model is needed immediately, try to read from the local cache before performing an upgrade
     */
    override suspend fun tryLoadCachedModel(criticalPath: Boolean): LoadedModelMeta =
        if (criticalPath) {
            getLatestFile()?.let { LoadedModelMeta(it.name, readFileToByteBuffer(it)) }
                ?: LoadedModelMeta(defaultModelVersion, null)
        } else {
            LoadedModelMeta(defaultModelVersion, null)
        }

    /**
     * If the latest model has already been downloaded, load it into memory.
     */
    override suspend fun tryLoadCachedModel(hash: String, hashAlgorithm: String): LoadedModelMeta =
        getMatchingFile(hash, hashAlgorithm)?.let { LoadedModelMeta(it.name, readFileToByteBuffer(it)) }
            ?: LoadedModelMeta(defaultModelVersion, null)

    override suspend fun getDownloadOutputFile(modelVersion: String) = File(cacheFolder, modelVersion)

    override suspend fun getDownloadDetails(): DownloadDetails? {
        cachedDownloadDetails?.let {
            return DownloadDetails(url, hash, hashAlgorithm, modelVersion)
        }

        return when (val modelUpgradeResponse = getModelUpgradePath(context, modelClass, modelFrameworkVersion)) {
            is NetworkResult.Success ->
                try {
                    DownloadDetails(
                        url = URL(modelUpgradeResponse.body.modelUrl),
                        hash = modelUpgradeResponse.body.hash,
                        hashAlgorithm = modelUpgradeResponse.body.hashAlgorithm,
                        modelVersion = modelUpgradeResponse.body.modelVersion
                    ).apply { cachedDownloadDetails = this }
                } catch (t: Throwable) {
                    Log.e(Config.logTag, "Invalid signed url for model $modelClass: ${modelUpgradeResponse.body.modelUrl}")
                    null
                }
            else -> {
                Log.e(Config.logTag, "Failed to get latest details for model $modelClass: ${modelUpgradeResponse.responseCode}")
                fallbackDownloadDetails()
            }
        }
    }

    /**
     * Fall back to getting the download details.
     */
    protected open suspend fun fallbackDownloadDetails() =
        super.getDownloadDetails()?.apply { cachedDownloadDetails = this }

    /**
     * Delete all files in cache that are not the recently downloaded file.
     */
    override suspend fun cleanUpPostDownload(downloadedFile: File) = withContext(Dispatchers.IO) {
        cacheFolder
            .listFiles()
            ?.filter { it != downloadedFile && calculateHash(it, defaultModelHashAlgorithm) != defaultModelHash }
            ?.sortedByDescending { it.lastModified() }
            ?.filterIndexed { index, file -> file.lastModified().asEpochMillisecondsClockMark().elapsedSince() > CACHE_MODEL_TIME || index > CACHE_MODEL_MAX_COUNT }
            ?.forEach { it.delete() }
            .let { Unit }
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
 * A loader that queries Bouncer servers for updated models. If a new version is found, download it. If the model
 * details match what is cached, return those instead.
 */
abstract class UpdatingResourceLoader(private val context: Context) : UpdatingModelWebLoader(context) {
    protected abstract val resource: Int
    protected abstract val resourceModelVersion: String
    protected abstract val resourceModelHash: String
    protected abstract val resourceModelHashAlgorithm: String

    override val defaultModelFileName: String = ""
    override val defaultModelVersion: String by lazy { resourceModelVersion }
    override val defaultModelHash: String by lazy { resourceModelHash }
    override val defaultModelHashAlgorithm: String by lazy { resourceModelHashAlgorithm }

    override suspend fun tryLoadCachedModel(criticalPath: Boolean): LoadedModelMeta = if (criticalPath) {
        super.tryLoadCachedModel(criticalPath).run {
            if (model == null) {
                loadModelFromResource()
            } else {
                this
            }
        }
    } else {
        LoadedModelMeta(resourceModelVersion, null)
    }

    override suspend fun fallbackDownloadDetails(): DownloadDetails? = DownloadDetails(
        url = URL("https://localhost"),
        hash = resourceModelHash,
        hashAlgorithm = resourceModelHashAlgorithm,
        modelVersion = resourceModelVersion
    )

    override suspend fun tryLoadCachedModel(hash: String, hashAlgorithm: String): LoadedModelMeta =
        if (hash == defaultModelHash && hashAlgorithm == defaultModelHashAlgorithm) {
            loadModelFromResource()
        } else {
            super.tryLoadCachedModel(hash, hashAlgorithm)
        }

    private fun loadModelFromResource(): LoadedModelMeta = try {
        LoadedModelMeta(
            modelVersion = resourceModelVersion,
            model = context.resources.openRawResourceFd(resource).use { fileDescriptor ->
                FileInputStream(fileDescriptor.fileDescriptor).use { input ->
                    val data = readFileToByteBuffer(
                        input,
                        fileDescriptor.startOffset,
                        fileDescriptor.declaredLength
                    )
                    data
                }
            }
        )
    } catch (t: Throwable) {
        Log.e(Config.logTag, "Failed to load resource", t)
        LoadedModelMeta(
            modelVersion = resourceModelVersion,
            model = null
        )
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
