package com.getbouncer.scan.framework

import com.getbouncer.scan.framework.time.Clock
import com.getbouncer.scan.framework.time.ClockMark
import com.getbouncer.scan.framework.time.Duration
import com.getbouncer.scan.framework.time.measureTime
import com.getbouncer.scan.framework.time.milliseconds
import com.getbouncer.scan.framework.time.min
import com.getbouncer.scan.framework.time.seconds
import java.util.LinkedList
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

private val MAX_ANALYZER_DELAY = 1.seconds
private val MIN_ANALYZER_DELAY = 50.milliseconds

data class LoopState<State>(
    val startedAt: ClockMark?,
    val finished: Boolean,
    val state: State
)

object NoAnalyzersAvailableException : Exception()

/**
 * A loop to execute repeated analysis. The loop uses coroutines to run the [Analyzer.analyze] method. If the [Analyzer]
 * is threadsafe, multiple coroutines will be used. If not, a single coroutine will be used.
 *
 * Any data enqueued while the analyzers are at capacity will be dropped.
 *
 * This will process data until [state].finished is true.
 *
 * Note: an analyzer loop can only be started once. Once it terminates, it cannot be restarted.
 *
 * @param analyzerPool: A pool of analyzers to use in this loop.
 * @param onAnalyzerFailure: This function will fire when a coroutine throws an exception. If this method returns true,
 *     the loop will terminate. If this method returns false, the loop will continue to execute.
 * @param initialState: The initial state for this loop.
 */
sealed class AnalyzerLoop<DataFrame, State, Output>(
    private val analyzerPool: AnalyzerPool<DataFrame, State, Output>,
    private val onAnalyzerFailure: (t: Throwable) -> Boolean,
    private val onResultFailure: (t: Throwable) -> Boolean,
    initialState: State
) : StateUpdatingResultHandler<DataFrame, LoopState<State>, Output> {
    private val started = AtomicBoolean(false)
    private val channel by lazy { Channel<DataFrame>(calculateChannelBufferSize()) }

    private val startMutex = Mutex()
    private val cancelMutex = Mutex()
    private val executionDurationMutex = Mutex()

    private var state = LoopState(
        startedAt = null,
        finished = false,
        state = initialState
    )

    private val analyzerExecutionDurations = LinkedList<Duration>()
    protected var averageAnalyzerExecutionDuration = MIN_ANALYZER_DELAY
        private set

    internal abstract val name: String

    private lateinit var loopExecutionStatTracker: StatTracker

    abstract fun calculateChannelBufferSize(): Int

    open suspend fun processFrame(frame: DataFrame) = if (shouldReceiveNewFrame(state)) channel.offer(frame) else false

    abstract suspend fun shouldReceiveNewFrame(state: LoopState<State>): Boolean

    private val updateState: (LoopState<State>) -> Unit = { state = it }

    open suspend fun start(processingCoroutineScope: CoroutineScope) {
        startMutex.withLock {
            if (state.startedAt == null) {
                updateState(state.copy(startedAt = Clock.markNow()))
            } else {
                return
            }
        }

        loopExecutionStatTracker = Stats.trackTask("loop_execution:$name")

        if (analyzerPool.analyzers.isEmpty()) {
            loopExecutionStatTracker.trackResult("canceled")
            onAnalyzerFailure(NoAnalyzersAvailableException)
            return
        }

        processingCoroutineScope.launch {
            val workerScope = this
            analyzerPool.analyzers.forEachIndexed { index, analyzer ->
                launch(Dispatchers.Default) {
                    startWorker(workerScope, index, analyzer)
                }
            }
        }
    }

    /**
     * Launch a worker coroutine that has access to the analyzer's `analyze` method and the result handler
     */
    private suspend fun startWorker(
        workerScope: CoroutineScope,
        workerId: Int,
        analyzer: Analyzer<DataFrame, State, Output>
    ) {
        for (frame in channel) {
            val stat = Stats.trackRepeatingTask("analyzer_execution:$name:${analyzer.name}")
            updateAnalyzerExecutionDuration(measureTime {
                try {
                    val analyzerResult = analyzer.analyze(frame, state.state)

                    try {
                        onResult(analyzerResult, state, frame, updateState)
                    } catch (t: Throwable) {
                        stat.trackResult("result_failure")
                        handleResultFailure(workerScope, t)
                    }
                } catch (t: Throwable) {
                    stat.trackResult("analyzer_failure")
                    handleAnalyzerFailure(workerScope, t)
                }
            })

            cancelMutex.withLock {
                if (state.finished && workerScope.isActive) {
                    loopExecutionStatTracker.trackResult("success:$workerId")
                    workerScope.cancel()
                }
            }

            stat.trackResult("success")
        }
    }

    private suspend fun updateAnalyzerExecutionDuration(newDuration: Duration) =
        executionDurationMutex.withLock {
            analyzerExecutionDurations.add(newDuration)
            if (analyzerExecutionDurations.size > analyzerPool.analyzers.size && analyzerExecutionDurations.size > 0) {
                analyzerExecutionDurations.removeFirst()
            }

            averageAnalyzerExecutionDuration = when {
                analyzerExecutionDurations.size > 1 -> analyzerExecutionDurations
                    .reduce { a, b -> a + b } / analyzerExecutionDurations.size
                analyzerExecutionDurations.size == 1 -> analyzerExecutionDurations.first
                else -> MIN_ANALYZER_DELAY
            }
        }

    private suspend fun handleAnalyzerFailure(workerScope: CoroutineScope, t: Throwable) {
        if (withContext(Dispatchers.Main) { onAnalyzerFailure(t) }) {
            cancelMutex.withLock {
                if (workerScope.isActive) {
                    workerScope.cancel()
                }
            }
        }
    }

    private suspend fun handleResultFailure(workerScope: CoroutineScope, t: Throwable) {
        if (withContext(Dispatchers.Main) { onResultFailure(t) }) {
            cancelMutex.withLock {
                if (workerScope.isActive) {
                    workerScope.cancel()
                }
            }
        }
    }
}

/**
 * This kind of [AnalyzerLoop] will process data until the result handler indicates that it has reached a terminal
 * state and is no longer listening.
 *
 * Data can be added to a queue for processing by a camera or other producer. It will be consumed by FILO. If no data
 * is available, the analyzer pauses until data becomes available.
 *
 * If the enqueued data exceeds the allowed memory size, the bottom of the data stack will be dropped and will not be
 * processed. This alleviates memory pressure when producers are faster than the consuming analyzer.
 *
 * Any data enqueued via [processFrame] will be dropped once this loop has terminated.
 *
 * @param analyzerPool: A pool of analyzers to use in this loop.
 * @param resultHandler: A result handler that will be called with the results from the analyzers in this loop.
 * @param name: The name of this loop for stat and event tracking.
 * @param onAnalyzerFailure: This function will fire when a coroutine throws an exception. If this method returns true,
 *     the loop will terminate. If this method returns false, the loop will continue to execute.
 */
class ProcessBoundAnalyzerLoop<DataFrame, State, Output>(
    private val analyzerPool: AnalyzerPool<DataFrame, State, Output>,
    private val resultHandler: StateUpdatingResultHandler<DataFrame, LoopState<State>, Output>,
    initialState: State,
    override val name: String,
    onAnalyzerFailure: (t: Throwable) -> Boolean,
    onResultFailure: (t: Throwable) -> Boolean
) : AnalyzerLoop<DataFrame, State, Output>(
    analyzerPool,
    onAnalyzerFailure,
    onResultFailure,
    initialState
) {
    private val shouldReceivedFrameMutex = Mutex()
    private var lastFrameReceivedAt: ClockMark? = null

    /**
     * Determine if a new frame should be processed. This method will stagger frames based on the amount of time the
     * analyzers take to execute. Staggering the frames evens out the rate at which they are processed.
     */
    override suspend fun shouldReceiveNewFrame(state: LoopState<State>): Boolean =
        shouldReceivedFrameMutex.withLock {
            val running = state.startedAt != null && !state.finished
            val analyzerDelay = min(averageAnalyzerExecutionDuration, MAX_ANALYZER_DELAY) / analyzerPool.desiredAnalyzerCount
            val shouldReceiveNewFrame =
                    running && lastFrameReceivedAt?.elapsedSince() ?: Duration.INFINITE > analyzerDelay
            if (shouldReceiveNewFrame) {
                this.lastFrameReceivedAt = Clock.markNow()
            }
            shouldReceiveNewFrame
        }

    /**
     * Do not queue frames since we want to process them as immediately as possible.
     */
    override fun calculateChannelBufferSize(): Int = Channel.RENDEZVOUS

    override suspend fun onResult(
        result: Output,
        state: LoopState<State>,
        data: DataFrame,
        updateState: (LoopState<State>) -> Unit
    ) = resultHandler.onResult(result, state, data, updateState)
}

/**
 * This kind of [AnalyzerLoop] will process data provided as part of its constructor. Data will be processed in the
 * order provided.
 *
 * @param frames: The list of data frames this loop will process.
 * @param analyzerPool: A pool of analyzers to use in this loop.
 * @param resultHandler: A result handler that will be called with the results from the analyzers in this loop.
 * @param name: The name of this loop for stat and event tracking.
 * @param onAnalyzerFailure: This function will fire when a coroutine throws an exception. If this method returns true,
 *     the loop will terminate. If this method returns false, the loop will continue to execute.
 * @param timeLimit: If specified, this is the maximum allowed time for the loop to run. If the loop
 *     exceeds this duration, the loop will terminate
 */
class FiniteAnalyzerLoop<DataFrame, State, Output>(
    private val frames: Collection<DataFrame>,
    analyzerPool: AnalyzerPool<DataFrame, State, Output>,
    private val resultHandler: TerminatingResultHandler<DataFrame, State, Output>,
    initialState: State,
    override val name: String,
    onAnalyzerFailure: (t: Throwable) -> Boolean,
    onResultFailure: (t: Throwable) -> Boolean,
    private val timeLimit: Duration = Duration.INFINITE
) : AnalyzerLoop<DataFrame, State, Output>(
    analyzerPool,
    onAnalyzerFailure,
    onResultFailure,
    initialState
) {
    private val processFrameMutex = Mutex()

    private val framesToProcess: AtomicInteger = AtomicInteger(0)
    private val framesProcessed: AtomicInteger = AtomicInteger(0)

    override suspend fun start(processingCoroutineScope: CoroutineScope) {
        val framesToProcess = frames.map { processFrame(it) }.any()
        if (framesToProcess) {
            super.start(processingCoroutineScope)
        } else {
            resultHandler.onAllDataProcessed()
        }
    }

    override suspend fun processFrame(frame: DataFrame): Boolean = processFrameMutex.withLock {
        if (framesToProcess.get() < frames.size) {
            val result = super.processFrame(frame)
            if (result) {
                framesToProcess.incrementAndGet()
            }
            result
        } else {
            false
        }
    }

    override fun calculateChannelBufferSize(): Int = frames.size

    override suspend fun shouldReceiveNewFrame(state: LoopState<State>): Boolean = state.startedAt == null

    override suspend fun onResult(
        result: Output,
        state: LoopState<State>,
        data: DataFrame,
        updateState: (LoopState<State>) -> Unit
    ) {
        val framesProcessed = this.framesProcessed.incrementAndGet()
        val timeElapsed = state.startedAt?.elapsedSince() ?: Duration.ZERO
        resultHandler.onResult(result, state.state, data)

        if (framesProcessed >= frames.size) {
            resultHandler.onAllDataProcessed()
        } else if (timeElapsed > timeLimit) {
            resultHandler.onTerminatedEarly()
        }

        if (isFinished(state)) {
            updateState(state.copy(finished = true))
        }
    }

    private fun isFinished(state: LoopState<State>): Boolean {
        val timeElapsed = state.startedAt?.elapsedSince() ?: Duration.ZERO
        val allFramesProcessed = framesProcessed.get() >= frames.size
        val exceededTimeLimit = timeElapsed > timeLimit

        return allFramesProcessed || exceededTimeLimit
    }
}
