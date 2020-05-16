package com.getbouncer.scan.framework

import androidx.test.filters.MediumTest
import com.getbouncer.scan.framework.time.milliseconds
import com.getbouncer.scan.framework.time.nanoseconds
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.fail
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.Test

class LoopTest {

    @Test(timeout = 1000)
    @MediumTest
    fun processBoundAnalyzerLoop_analyzeData() {
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

        val analyzerPool = runBlocking { AnalyzerPool.Factory(
            analyzerFactory = TestAnalyzerFactory(),
            desiredAnalyzerCount = 12
        ).buildAnalyzerPool() }

        val loop = ProcessBoundAnalyzerLoop(
            analyzerPool = analyzerPool,
            onAnalyzerFailure = { fail() },
            onResultFailure = { fail() },
            initialState = 1,
            name = "TestAnalyzerLoop",
            resultHandler = TestResultHandler()
        )

        runBlocking {
            loop.start(GlobalScope)
        }

        repeat(dataCount) {
            assertTrue { runBlocking {
                delay(10)
                loop.processFrame(2)
            } }
        }

        while (resultCount.get() < dataCount) {
            Thread.sleep(10)
        }
    }

    @Test(timeout = 1000)
    @MediumTest
    fun processBoundAnalyzerLoop_noAnalyzersAvailable() {
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

        val analyzerPool = runBlocking { AnalyzerPool.Factory(
            analyzerFactory = TestAnalyzerFactory(),
            desiredAnalyzerCount = 0
        ).buildAnalyzerPool() }

        val loop = ProcessBoundAnalyzerLoop(
            analyzerPool = analyzerPool,
            onAnalyzerFailure = { analyzerFailure = true; true },
            onResultFailure = { fail() },
            initialState = 1,
            name = "TestAnalyzerLoop",
            resultHandler = TestResultHandler()
        )

        runBlocking {
            loop.start(GlobalScope)
        }

        while (!analyzerFailure) {
            Thread.sleep(10)
        }
    }

    @Test(timeout = 1000)
    @MediumTest
    fun finiteAnalyzerLoop_analyzeData() {
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

        val analyzerPool = runBlocking { AnalyzerPool.Factory(
            analyzerFactory = TestAnalyzerFactory(),
            desiredAnalyzerCount = 12
        ).buildAnalyzerPool() }

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

        runBlocking {
            loop.start(GlobalScope)
        }

        while (!dataProcessed) {
            Thread.sleep(10)
        }
    }

    @Test(timeout = 1000)
    @MediumTest
    fun finiteAnalyzerLoop_analyzeDataTimeout() {
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

        val analyzerPool = runBlocking { AnalyzerPool.Factory(
            analyzerFactory = TestAnalyzerFactory(),
            desiredAnalyzerCount = 12
        ).buildAnalyzerPool() }

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

        runBlocking {
            loop.start(GlobalScope)
        }

        while (!terminatedEarly) {
            Thread.sleep(10)
        }
    }

    private class TestAnalyzer : Analyzer<Int, Int, Int> {
        override val name: String = "TestAnalyzer"
        override suspend fun analyze(data: Int, state: Int): Int = data + state
    }

    private class TestAnalyzerFactory : AnalyzerFactory<TestAnalyzer> {
        override val isThreadSafe: Boolean = true
        override suspend fun newInstance(): TestAnalyzer? = TestAnalyzer()
    }
}
