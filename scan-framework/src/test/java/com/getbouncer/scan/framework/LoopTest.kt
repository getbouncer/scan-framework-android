package com.getbouncer.scan.framework

import androidx.test.filters.SmallTest
import com.getbouncer.scan.framework.time.milliseconds
import com.getbouncer.scan.framework.time.nanoseconds
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.test.runBlockingTest
import kotlinx.coroutines.yield
import org.junit.Test
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.fail

class LoopTest {

    @Test(timeout = 1000)
    @SmallTest
    @ExperimentalCoroutinesApi
    fun processBoundAnalyzerLoop_analyzeData() = runBlockingTest {
        val dataCount = 3
        val resultCount = AtomicInteger(0)

        class TestResultHandler : StateUpdatingResultHandler<Int, LoopState<Int>, Int> {
            override suspend fun onResult(
                result: Int,
                state: LoopState<Int>,
                data: Int,
                updateState: (LoopState<Int>) -> Unit
            ) {
                assertEquals(2, data)
                assertEquals(1, state.state)
                assertEquals(3, result)
                resultCount.incrementAndGet()
            }
        }

        val analyzerPool = AnalyzerPoolFactory(
            analyzerFactory = TestAnalyzerFactory(),
            desiredAnalyzerCount = 12
        ).buildAnalyzerPool()

        val loop = ProcessBoundAnalyzerLoop(
            analyzerPool = analyzerPool,
            onAnalyzerFailure = { fail() },
            onResultFailure = { fail() },
            initialState = 1,
            name = "TestAnalyzerLoop",
            resultHandler = TestResultHandler()
        )

        val channel = Channel<Int>(capacity = Channel.RENDEZVOUS)
        loop.subscribeTo(channel, GlobalScope)

        repeat(dataCount) {
            var processedFrame = false
            while (!processedFrame) {
                processedFrame = channel.offer(2)
                yield()
            }
        }

        while (dataCount > resultCount.get()) {
            yield()
        }

        assertEquals(dataCount, resultCount.get())
    }

    @Test(timeout = 1000)
    @SmallTest
    @ExperimentalCoroutinesApi
    fun processBoundAnalyzerLoop_noAnalyzersAvailable() = runBlockingTest {
        var analyzerFailure = false

        class TestResultHandler : StateUpdatingResultHandler<Int, LoopState<Int>, Int> {
            override suspend fun onResult(
                result: Int,
                state: LoopState<Int>,
                data: Int,
                updateState: (LoopState<Int>) -> Unit
            ) {
                assertEquals(2, data)
                assertEquals(1, state.state)
                assertEquals(3, result)
            }
        }

        val analyzerPool = AnalyzerPoolFactory(
            analyzerFactory = TestAnalyzerFactory(),
            desiredAnalyzerCount = 0
        ).buildAnalyzerPool()

        val loop = ProcessBoundAnalyzerLoop(
            analyzerPool = analyzerPool,
            onAnalyzerFailure = { analyzerFailure = true; true },
            onResultFailure = { fail() },
            initialState = 1,
            name = "TestAnalyzerLoop",
            resultHandler = TestResultHandler()
        )

        val channel = Channel<Int>(capacity = Channel.RENDEZVOUS)
        loop.subscribeTo(channel, GlobalScope)

        while (!analyzerFailure) {
            yield()
        }
    }

    @Test(timeout = 1000)
    @SmallTest
    @ExperimentalCoroutinesApi
    fun finiteAnalyzerLoop_analyzeData() = runBlockingTest {
        val dataCount = 3
        var dataProcessed = false
        val resultCount = AtomicInteger(0)

        class TestResultHandler : TerminatingResultHandler<Int, Int, Int> {
            override suspend fun onResult(result: Int, state: Int, data: Int) {
                resultCount.incrementAndGet()
                assertEquals(2, data)
                assertEquals(1, state)
                assertEquals(3, result)
            }

            override suspend fun onAllDataProcessed() {
                assertEquals(dataCount, resultCount.get())
                dataProcessed = true
            }

            override suspend fun onTerminatedEarly() {
                fail()
            }
        }

        val analyzerPool = AnalyzerPoolFactory(
            analyzerFactory = TestAnalyzerFactory(),
            desiredAnalyzerCount = 12
        ).buildAnalyzerPool()

        val loop = FiniteAnalyzerLoop(
            analyzerPool = analyzerPool,
            onAnalyzerFailure = { fail(it.message) },
            onResultFailure = { fail(it.message) },
            initialState = 1,
            name = "TestAnalyzerLoop",
            resultHandler = TestResultHandler(),
            frames = (0 until dataCount).map { 2 },
            timeLimit = 500.milliseconds
        )

        loop.start(GlobalScope)

        while (!dataProcessed) {
            yield()
        }
    }

    @Test(timeout = 1000)
    @SmallTest
    @ExperimentalCoroutinesApi
    fun finiteAnalyzerLoop_analyzeDataTimeout() = runBlockingTest {
        val dataCount = 10000
        val resultCount = AtomicInteger(0)
        var terminatedEarly = false

        class TestResultHandler : TerminatingResultHandler<Int, Int, Int> {
            override suspend fun onResult(result: Int, state: Int, data: Int) {
                assertEquals(2, data)
                assertEquals(1, state)
                assertEquals(3, result)
                resultCount.incrementAndGet()
            }

            override suspend fun onAllDataProcessed() {
                fail()
            }

            override suspend fun onTerminatedEarly() {
                assertTrue { resultCount.get() < dataCount }
                terminatedEarly = true
            }
        }

        val analyzerPool = AnalyzerPoolFactory(
            analyzerFactory = TestAnalyzerFactory(),
            desiredAnalyzerCount = 12
        ).buildAnalyzerPool()

        val loop = FiniteAnalyzerLoop(
            analyzerPool = analyzerPool,
            onAnalyzerFailure = { fail() },
            onResultFailure = { fail() },
            initialState = 1,
            name = "TestAnalyzerLoop",
            resultHandler = TestResultHandler(),
            frames = (0 until dataCount).map { 2 },
            timeLimit = 1.nanoseconds
        )

        loop.start(this)

        while (!terminatedEarly) {
            yield()
        }
    }

    @Test(timeout = 1000)
    @SmallTest
    @ExperimentalCoroutinesApi
    fun finiteAnalyzerLoop_analyzeDataNoData() = runBlockingTest {
        var dataProcessed = false

        class TestResultHandler : TerminatingResultHandler<Int, Int, Int> {
            override suspend fun onResult(result: Int, state: Int, data: Int) { fail() }

            override suspend fun onAllDataProcessed() { dataProcessed = true }

            override suspend fun onTerminatedEarly() { fail() }
        }

        val analyzerPool = AnalyzerPoolFactory(
            analyzerFactory = TestAnalyzerFactory(),
            desiredAnalyzerCount = 12
        ).buildAnalyzerPool()

        val loop = FiniteAnalyzerLoop(
            analyzerPool = analyzerPool,
            onAnalyzerFailure = { fail(it.message) },
            onResultFailure = { fail(it.message) },
            initialState = 1,
            name = "TestAnalyzerLoop",
            resultHandler = TestResultHandler(),
            frames = emptyList(),
            timeLimit = 500.milliseconds
        )

        loop.start(this)

        while (!dataProcessed) {
            yield()
        }
    }

    private class TestAnalyzer : Analyzer<Int, Int, Int> {
        override val name: String = "TestAnalyzer"
        override suspend fun analyze(data: Int, state: Int): Int = data + state
    }

    private class TestAnalyzerFactory : AnalyzerFactory<TestAnalyzer> {
        override suspend fun newInstance(): TestAnalyzer? = TestAnalyzer()
    }
}
