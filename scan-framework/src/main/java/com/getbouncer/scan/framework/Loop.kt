package com.getbouncer.scan.framework

import android.util.Log
import com.getbouncer.scan.framework.time.Clock
import com.getbouncer.scan.framework.time.ClockMark
import com.getbouncer.scan.framework.time.Duration
import com.getbouncer.scan.framework.time.measureTime
import kotlinx.coroutines.CompletableJob
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.buffer
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import kotlin.coroutines.suspendCoroutine

data class LoopState<State>(
    val startedAt: ClockMark?,
    val finished: Boolean,
    val state: State
)

object NoAnalyzersAvailableException : Exception()

object AlreadySubscribedException : Exception()

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

    private val cancelMutex = Mutex()

    private var state = LoopState(
        startedAt = null,
        finished = false,
        state = initialState
    )

    internal abstract val name: String

    private lateinit var loopExecutionStatTracker: StatTracker

    private val updateState: (LoopState<State>) -> Unit = { state = it }

    private var workerJob: Job? = null

    protected fun subscribeToFlow(
        flow: Flow<DataFrame>,
        processingCoroutineScope: CoroutineScope
    ): Job? {
        if (!started.getAndSet(true)) {
            updateState(state.copy(startedAt = Clock.markNow()))
        } else {
            onAnalyzerFailure(AlreadySubscribedException)
            return null
        }

        loopExecutionStatTracker = Stats.trackTask("loop_execution:$name")

        if (analyzerPool.analyzers.isEmpty()) {
            runBlocking { loopExecutionStatTracker.trackResult("canceled") }
            onAnalyzerFailure(NoAnalyzersAvailableException)
            return null
        }

        workerJob = processingCoroutineScope.launch {
            analyzerPool.analyzers.forEachIndexed { index, analyzer ->
                launch(Dispatchers.Default) {
                    startWorker(flow, index, analyzer)
                }
            }
        }

        return workerJob
    }

    protected suspend fun unsubscribeFromFlow() = cancelMutex.withLock {
        workerJob?.apply { if (isActive) { cancel() } }
        started.set(false)
    }

    /**
     * Launch a worker coroutine that has access to the analyzer's `analyze` method and the result handler
     */
    private suspend fun startWorker(
        flow: Flow<DataFrame>,
        workerId: Int,
        analyzer: Analyzer<DataFrame, State, Output>
    ) {
        flow.collect { frame ->
            val stat = Stats.trackRepeatingTask("analyzer_execution:$name:${analyzer.name}")
            measureTime {
                try {
                    val analyzerResult = analyzer.analyze(frame, state.state)

                    try {
                        onResult(analyzerResult, state, frame, updateState)
                    } catch (t: Throwable) {
                        stat.trackResult("result_failure")
                        handleResultFailure(t)
                    }
                } catch (t: Throwable) {
                    stat.trackResult("analyzer_failure")
                    handleAnalyzerFailure(t)
                }
            }

            cancelMutex.withLock {
                if (state.finished) {
                    loopExecutionStatTracker.trackResult("success:$workerId")
                    unsubscribeFromFlow()
                }
            }

            stat.trackResult("success")
        }
    }

    private suspend fun handleAnalyzerFailure(t: Throwable) {
        if (withContext(Dispatchers.Main) { onAnalyzerFailure(t) }) { unsubscribeFromFlow() }
    }

    private suspend fun handleResultFailure(t: Throwable) {
        if (withContext(Dispatchers.Main) { onResultFailure(t) }) { unsubscribeFromFlow() }
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
    /**
     * Subscribe to a flow. Loops can only subscribe to a single flow at a time.
     */
    fun subscribeTo(flow: Flow<DataFrame>, processingCoroutineScope: CoroutineScope) =
        subscribeToFlow(flow, processingCoroutineScope)

    /**
     * Unsubscribe from the flow.
     */
    fun unsubscribe() = runBlocking { unsubscribeFromFlow() }

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
 * @param analyzerPool: A pool of analyzers to use in this loop.
 * @param resultHandler: A result handler that will be called with the results from the analyzers in this loop.
 * @param name: The name of this loop for stat and event tracking.
 * @param onAnalyzerFailure: This function will fire when a coroutine throws an exception. If this method returns true,
 *     the loop will terminate. If this method returns false, the loop will continue to execute.
 * @param timeLimit: If specified, this is the maximum allowed time for the loop to run. If the loop
 *     exceeds this duration, the loop will terminate
 */
class FiniteAnalyzerLoop<DataFrame, State, Output>(
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
    private val framesProcessed: AtomicInteger = AtomicInteger(0)
    private var framesToProcess = 0

    fun process(frames: Collection<DataFrame>, processingCoroutineScope: CoroutineScope): Job? {
        val channel = Channel<DataFrame>(capacity = frames.size)
        framesToProcess = frames.map { channel.offer(it) }.count { it }
        return if (framesToProcess > 0) {
            subscribeToFlow(channel.receiveAsFlow(), processingCoroutineScope)
        } else {
            processingCoroutineScope.launch {
                resultHandler.onAllDataProcessed()
            }
        }
    }

    override suspend fun onResult(
        result: Output,
        state: LoopState<State>,
        data: DataFrame,
        updateState: (LoopState<State>) -> Unit
    ) {
        val framesProcessed = this.framesProcessed.incrementAndGet()
        val timeElapsed = state.startedAt?.elapsedSince() ?: Duration.ZERO
        resultHandler.onResult(result, state.state, data)

        if (framesProcessed >= framesToProcess) {
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
        val allFramesProcessed = framesProcessed.get() >= framesToProcess
        val exceededTimeLimit = timeElapsed > timeLimit

        return allFramesProcessed || exceededTimeLimit
    }
}

/**
 * Consume this [Flow] using a channelFlow with no buffer. Elements emitted from [this] flow
 * are offered to the underlying [channelFlow]. If the consumer is not currently suspended and
 * waiting for the next element, the element is dropped.
 *
 * example:
 * ```
 * flow {
 *   (0..100).forEach {
 *     emit(it)
 *     delay(100)
 *   }
 * }.backPressureDrop().collect {
 *   delay(1000)
 *   println(it)
 * }
 * ```
 *
 * @return a flow that only emits elements when the downstream [Flow.collect] is waiting for the next element
 */
@ExperimentalCoroutinesApi
suspend fun <T> Flow<T>.backPressureDrop(): Flow<T> =
    channelFlow { collect { offer(it) } }.buffer(capacity = Channel.RENDEZVOUS)
