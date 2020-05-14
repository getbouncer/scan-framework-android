package com.getbouncer.scan.framework

import android.util.Log
import com.getbouncer.scan.framework.time.AtomicClockMark
import com.getbouncer.scan.framework.time.Clock
import com.getbouncer.scan.framework.time.ClockMark
import com.getbouncer.scan.framework.time.Duration
import com.getbouncer.scan.framework.time.Rate
import com.getbouncer.scan.framework.time.seconds
import java.util.LinkedList
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import kotlin.math.max
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * A specialized result handler for loops. This handler can update the state of the loop, including
 * asking for termination.
 */
interface StateUpdatingResultHandler<Input, State, Output> {
    suspend fun onResult(result: Output, state: State, data: Input, updateState: (State) -> Unit)
}

/**
 * A result handler for data processing. This is called when results are available from an
 * [Analyzer].
 */
interface ResultHandler<Input, State, Output> {
    suspend fun onResult(result: Output, state: State, data: Input)
}

/**
 * A frame and its result that is saved for later analysis.
 */
data class SavedFrame<DataFrame, State, Result>(val data: DataFrame, val state: State, val result: Result)

interface AggregateResultListener<DataFrame, State, AnalyzerResult, FinalResult> {

    /**
     * The aggregated result of an [AnalyzerLoop] is available.
     *
     * @param result: the result from the [AnalyzerLoop]
     * @param frames: data frames captured during processing that can be used in the completion loop
     */
    suspend fun onResult(result: FinalResult, frames: Map<String, List<SavedFrame<DataFrame, State, AnalyzerResult>>>)

    /**
     * An interim result is available, but the [AnalyzerLoop] is still processing more data frames.
     * This is useful for displaying a debug window.
     *
     * @param result: the result from the [AnalyzerLoop]
     * @param state: the shared [State] that produced this result
     * @param frame: the data frame that produced this result
     * @param isFirstValidResult: true if this is the first valid result
     */
    suspend fun onInterimResult(result: AnalyzerResult, state: State, frame: DataFrame, isFirstValidResult: Boolean)

    /**
     * An invalid result was received, but the [AnalyzerLoop] is still processing more data frames.
     * This is useful for displaying a debug window
     *
     * @param result: the result from the [AnalyzerLoop]
     * @param state: the shared [State] that produced this result
     * @param frame: the data frame that produced this result
     * @param hasPreviousValidResult: true if a previous interim result was valid
     */
    suspend fun onInvalidResult(result: AnalyzerResult, state: State, frame: DataFrame, hasPreviousValidResult: Boolean)

    /**
     * The result aggregator was reset back to its original state.
     */
    suspend fun onReset()
}

/**
 * A result handler with a method that notifies when all data has been processed.
 */
interface TerminatingResultHandler<Input, State, Output> : ResultHandler<Input, State, Output> {
    /**
     * All data has been processed and termination was reached.
     */
    suspend fun onAllDataProcessed()

    /**
     * Not all data was processed before termination.
     */
    suspend fun onTerminatedEarly()
}

/**
 * Configuration for a result aggregator
 */
data class ResultAggregatorConfig internal constructor(
    val maxTotalAggregationTime: Duration,
    val requiredSavedFrames: Map<String, Int>,
    val maxSavedFrames: Map<String, Int>,
    val defaultMaxSavedFrames: Int?,
    val frameStorageBytes: Map<String, Int>,
    val defaultFrameStorageBytes: Int?,
    val trackFrameRate: Boolean,
    val frameRateUpdateInterval: Duration
) {

    class Builder {
        companion object {
            private val DEFAULT_MAX_TOTAL_AGGREGATION_TIME = 1.5.seconds
            private val DEFAULT_MAX_SAVED_FRAMES = null // Frame storage is not limited by count
            private const val DEFAULT_FRAME_STORAGE_BYTES = 0x2000000 // 32MB
            private val DEFAULT_TRACK_FRAME_RATE = Config.isDebug
            private val DEFAULT_FRAME_RATE_UPDATE_INTERVAL = 1.seconds
        }

        private var maxTotalAggregationTime: Duration = DEFAULT_MAX_TOTAL_AGGREGATION_TIME

        private var requiredSavedFrames: MutableMap<String, Int> = mutableMapOf()

        private var maxSavedFrames: MutableMap<String, Int> = mutableMapOf()
        private var defaultMaxSavedFrames: Int? = DEFAULT_MAX_SAVED_FRAMES

        private var frameStorageBytes: MutableMap<String, Int> = mutableMapOf()
        private var defaultFrameStorageBytes: Int? = DEFAULT_FRAME_STORAGE_BYTES

        private var trackFrameRate: Boolean = DEFAULT_TRACK_FRAME_RATE
        private var frameRateUpdateInterval: Duration = DEFAULT_FRAME_RATE_UPDATE_INTERVAL

        fun withMaxTotalAggregationTime(maxTotalAggregationTime: Duration) = this.apply {
            this.maxTotalAggregationTime = maxTotalAggregationTime
        }

        fun withRequiredSavedFrames(requiredSavedFrames: Map<String, Int>) = this.apply {
            this.requiredSavedFrames = requiredSavedFrames.toMutableMap()
        }

        fun withRequiredSavedFrames(frameType: String, requiredSavedFrames: Int) = this.apply {
            this.requiredSavedFrames[frameType] = requiredSavedFrames
        }

        fun withMaxSavedFrames(maxSavedFrames: Map<String, Int>) = this.apply {
            this.maxSavedFrames = maxSavedFrames.toMutableMap()
        }

        fun withMaxSavedFrames(frameType: String, maxSavedFrames: Int) = this.apply {
            this.maxSavedFrames[frameType] = maxSavedFrames
        }

        fun withDefaultMaxSavedFrames(defaultMaxSavedFrames: Int) = this.apply {
            this.defaultMaxSavedFrames = defaultMaxSavedFrames
        }

        fun withFrameRateUpdateInterval(frameRateUpdateInterval: Duration) = this.apply {
            this.frameRateUpdateInterval = frameRateUpdateInterval
        }

        fun withFrameStorageBytes(frameStorageBytes: Map<String, Int>) = this.apply {
            this.frameStorageBytes = frameStorageBytes.toMutableMap()
        }

        fun withFrameStorageBytes(frameType: String, frameStorageBytes: Int) = this.apply {
            this.frameStorageBytes[frameType] = frameStorageBytes
        }

        fun withDefaultFrameStorageBytes(defaultFrameStorageBytes: Int) = this.apply {
            this.defaultFrameStorageBytes = defaultFrameStorageBytes
        }

        fun withTrackFrameRate(trackFrameRate: Boolean) = this.apply {
            this.trackFrameRate = trackFrameRate
        }

        fun build() =
            ResultAggregatorConfig(
                maxTotalAggregationTime,
                requiredSavedFrames,
                maxSavedFrames,
                defaultMaxSavedFrames,
                frameStorageBytes,
                defaultFrameStorageBytes,
                trackFrameRate,
                frameRateUpdateInterval
            )
    }
}

/**
 * The result aggregator processes results from an analyzer until a condition specified in the
 * configuration is met, either total aggregation time elapses or required agreement count is met.
 */
abstract class ResultAggregator<DataFrame, State, AnalyzerResult, FinalResult>(
    private val config: ResultAggregatorConfig,
    private val listener: AggregateResultListener<DataFrame, State, AnalyzerResult, FinalResult>,
    private val name: String
) : StateUpdatingResultHandler<DataFrame, LoopState<State>, AnalyzerResult> {

    private val firstResultTime = AtomicClockMark()
    private val firstFrameTime = AtomicClockMark()
    private var lastNotifyTime: ClockMark = Clock.markNow()
    private val totalFramesProcessed: AtomicLong = AtomicLong(0)
    private val framesProcessedSinceLastUpdate: AtomicLong = AtomicLong(0)
    private val haveSeenValidResult = AtomicBoolean(false)

    private var isPaused = false
    private var isFinished = false

    private val savedFrames = mutableMapOf<String, LinkedList<SavedFrame<DataFrame, State, AnalyzerResult>>>()
    private val savedFramesSizeBytes = mutableMapOf<String, Int>()

    private val aggregatorExecutionStats = runBlocking { Stats.trackRepeatingTask("${name}_aggregator_execution") }
    private val firstValidFrameStats = runBlocking { Stats.trackTask("${name}_aggregator_first_valid_frame") }

    private val saveFrameMutex = Mutex()
    private val frameRateMutex = Mutex()
    private val resultMutex = Mutex()

    /**
     * Reset the state of the aggregator and pause aggregation. This is useful for aggregating data
     * that can become invalid, such as when a user is scanning an object, and moves the object away
     * from the camera before the scan has completed.
     */
    open fun resetAndPause() {
        isPaused = true

        firstResultTime.clear()
        firstFrameTime.clear()
        totalFramesProcessed.set(0)
        framesProcessedSinceLastUpdate.set(0)
        haveSeenValidResult.set(false)
        savedFrames.clear()
        savedFramesSizeBytes.clear()
    }

    /**
     * Resume aggregation after it has been paused.
     */
    open fun resume() {
        isPaused = false
    }

    /**
     * Reset the state of the aggregator
     */
    protected suspend fun reset() {
        firstResultTime.clear()
        firstFrameTime.clear()
        haveSeenValidResult.set(false)

        saveFrameMutex.withLock {
            savedFrames.clear()
            savedFramesSizeBytes.clear()
        }

        listener.onReset()
    }

    override suspend fun onResult(
        result: AnalyzerResult,
        state: LoopState<State>,
        data: DataFrame,
        updateState: (LoopState<State>) -> Unit
    ) = resultMutex.withLock {
        if (state.finished || isPaused || isFinished) {
            return
        }

        withContext(Dispatchers.Default) {
            if (config.trackFrameRate) {
                trackAndNotifyOfFrameRate()
            }

            val validResult = isValidResult(result)
            if (validResult && firstResultTime.setFirstTime(Clock.markNow())) {
                firstValidFrameStats.trackResult("success")
            }

            val hasSavedEnoughFrames = saveFrames(result, state.state, data)

            if (validResult) {
                launch { listener.onInterimResult(
                    result = result,
                    state = state.state,
                    frame = data,
                    isFirstValidResult = !haveSeenValidResult.getAndSet(true)
                ) }
            } else {
                launch {
                    listener.onInvalidResult(result, state.state, data, haveSeenValidResult.get())
                }
            }

            val finalResult = aggregateResult(
                result = result,
                state = state.state,
                mustReturn = hasSavedEnoughFrames || hasReachedTimeout(),
                updateState = { updateState(state.copy(state = it)) }
            )

            aggregatorExecutionStats.trackResult("frame_processed")
            if (finalResult != null) {
                isFinished = true
                updateState(state.copy(finished = true))
                launch { listener.onResult(finalResult, savedFrames) }
            }
        }
    }

    /**
     * Determine how frames should be classified using [getSaveFrameIdentifier], and then store them
     * in a map of frames based on that identifier.
     *
     * This method keeps track of the total number of saved frames and the total size of saved
     * frames. If the total number or total size exceeds the maximum allowed in the aggregator
     * configuration, the oldest frames will be dropped.
     */
    private suspend fun saveFrames(result: AnalyzerResult, state: State, data: DataFrame): Boolean {
        val savedFrameType = getSaveFrameIdentifier(result, data) ?: return false
        return saveFrameMutex.withLock {
            val typedSavedFrames = savedFrames[savedFrameType] ?: LinkedList()

            val maxSavedFrames = config.maxSavedFrames[savedFrameType] ?: config.defaultMaxSavedFrames
            val storageBytes = config.frameStorageBytes[savedFrameType] ?: config.defaultFrameStorageBytes

            var typedSizeBytes = (savedFramesSizeBytes[savedFrameType] ?: 0) + getFrameSizeBytes(data)
            while (storageBytes != null && typedSizeBytes > storageBytes) {
                // saved frames is over storage limit, reduce until it's not
                if (typedSavedFrames.size > 0) {
                    val removedFrame = typedSavedFrames.removeFirst()
                    typedSizeBytes -= getFrameSizeBytes(removedFrame.data)
                } else {
                    typedSizeBytes = 0
                }
            }

            while (maxSavedFrames != null && typedSavedFrames.size > maxSavedFrames) {
                // saved frames is over size limit, reduce until it's not
                val removedFrame = typedSavedFrames.removeFirst()
                typedSizeBytes = max(0, typedSizeBytes - getFrameSizeBytes(removedFrame.data))
            }

            savedFramesSizeBytes[savedFrameType] = typedSizeBytes
            typedSavedFrames.add(SavedFrame(data, state, result))
            savedFrames[savedFrameType] = typedSavedFrames

            val requiredSavedFrames = config.requiredSavedFrames[savedFrameType] ?: Int.MAX_VALUE
            if (savedFrames[savedFrameType]?.size ?: 0 >= requiredSavedFrames) {
                return@withLock true
            }

            false
        }
    }

    /**
     * Aggregate a new result. Note that the [result] may be invalid. If this method returns a
     * non-null [AnalyzerResult], the aggregator will stop listening for new results.
     *
     * @param result: The result to aggregate
     * @param state: The loop state
     * @param mustReturn: If true, this method must return a final result
     * @param updateState: A function to use to update the state
     */
    abstract suspend fun aggregateResult(
        result: AnalyzerResult,
        state: State,
        mustReturn: Boolean,
        updateState: (State) -> Unit
    ): FinalResult?

    /**
     * Determine if a result is valid for tracking purposes.
     */
    abstract fun isValidResult(result: AnalyzerResult): Boolean

    /**
     * Determine if a data frame should be saved for future processing. Note that [result] may be
     * invalid.
     *
     * If this method returns a non-null string, the frame will be saved under that identifier.
     */
    abstract fun getSaveFrameIdentifier(result: AnalyzerResult, frame: DataFrame): String?

    /**
     * Determine the size in memory that this data frame takes up.
     */
    abstract fun getFrameSizeBytes(frame: DataFrame): Int

    /**
     * Calculate the current rate at which the [AnalyzerLoop] is processing images. Notify the
     * listener of the result.
     */
    private suspend fun trackAndNotifyOfFrameRate() {
        val totalFrames = totalFramesProcessed.incrementAndGet()
        val framesSinceLastUpdate = framesProcessedSinceLastUpdate.incrementAndGet()

        val lastNotifyTime = this.lastNotifyTime
        val shouldNotifyOfFrameRate = frameRateMutex.withLock {
            val shouldNotify = shouldNotifyOfFrameRate(this.lastNotifyTime)
            if (shouldNotify) {
                this.lastNotifyTime = Clock.markNow()
            }
            shouldNotify
        }

        if (shouldNotifyOfFrameRate) {
            val isFirstFrame = this.firstFrameTime.setFirstTime(Clock.markNow())
            val firstFrameTime = this.firstFrameTime.get()

            if (!isFirstFrame && firstFrameTime != null) {
                val totalFrameRate = Rate(totalFrames, firstFrameTime.elapsedSince())
                val instantFrameRate = Rate(framesSinceLastUpdate, lastNotifyTime.elapsedSince())

                logProcessingRate(totalFrameRate, instantFrameRate)
            }

            framesProcessedSinceLastUpdate.set(0)
        }
    }

    /**
     * Allow aggregators to get an immutable list of frames.
     */
    protected fun getSavedFrames(): Map<String, LinkedList<SavedFrame<DataFrame, State, AnalyzerResult>>> =
        savedFrames

    /**
     * Determine if enough time has elapsed since the last frame rate update.
     */
    private fun shouldNotifyOfFrameRate(lastNotifyTime: ClockMark) =
        lastNotifyTime.elapsedSince() > config.frameRateUpdateInterval

    /**
     * The processing rate has been updated. This is useful for debugging and measuring performance.
     *
     * @param overallRate: The total frame rate at which the analyzer is running
     * @param instantRate: The instantaneous frame rate at which the analyzer is running
     */
    private fun logProcessingRate(overallRate: Rate, instantRate: Rate) {
        val overallFps = if (overallRate.duration != Duration.ZERO) {
            overallRate.amount / overallRate.duration.inSeconds
        } else {
            0.0F
        }

        val instantFps = if (instantRate.duration != Duration.ZERO) {
            instantRate.amount / instantRate.duration.inSeconds
        } else {
            0.0F
        }

        if (Config.isDebug) {
            Log.d(Config.logTag, "Aggregator $name processing avg=$overallFps, inst=$instantFps")
        }
    }

    /**
     * Determine if the timeout from the config has been reached
     */
    private fun hasReachedTimeout(): Boolean {
        val firstResultTime = this.firstResultTime.get()
        return firstResultTime != null &&
                firstResultTime.elapsedSince() > config.maxTotalAggregationTime
    }
}
