package com.getbouncer.scan.framework

import androidx.test.filters.SmallTest
import kotlin.test.assertEquals
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runBlockingTest
import org.junit.Test

class AnalyzerTest {

    @Test
    @SmallTest
    @ExperimentalCoroutinesApi
    fun analyzerPoolCreateNormally() = runBlockingTest {
        class TestAnalyzerFactory : AnalyzerFactory<TestAnalyzer> {
            override val isThreadSafe: Boolean = true
            override suspend fun newInstance(): TestAnalyzer? = TestAnalyzer()
        }

        val analyzerPool = AnalyzerPool.Factory(
            analyzerFactory = TestAnalyzerFactory(),
            desiredAnalyzerCount = 12
        ).buildAnalyzerPool()

        assertEquals(12, analyzerPool.desiredAnalyzerCount)
        assertEquals(12, analyzerPool.analyzers.size)
        assertEquals(3, analyzerPool.analyzers[0].analyze(1, 2))
    }

    @Test
    @SmallTest
    @ExperimentalCoroutinesApi
    fun analyzerPoolCreateFailure() = runBlockingTest {
        class TestAnalyzerFactory : AnalyzerFactory<TestAnalyzer> {
            override val isThreadSafe: Boolean = true
            override suspend fun newInstance(): TestAnalyzer? = null
        }

        val analyzerPool = AnalyzerPool.Factory(
            analyzerFactory = TestAnalyzerFactory(),
            desiredAnalyzerCount = 12
        ).buildAnalyzerPool()

        assertEquals(12, analyzerPool.desiredAnalyzerCount)
        assertEquals(0, analyzerPool.analyzers.size)
    }

    @Test
    @SmallTest
    @ExperimentalCoroutinesApi
    fun analyzerPoolSingleThreaded() = runBlockingTest {
        class TestAnalyzerFactory : AnalyzerFactory<TestAnalyzer> {
            override val isThreadSafe: Boolean = false
            override suspend fun newInstance(): TestAnalyzer? = TestAnalyzer()
        }

        val analyzerPool = AnalyzerPool.Factory(
            analyzerFactory = TestAnalyzerFactory(),
            desiredAnalyzerCount = 12
        ).buildAnalyzerPool()

        assertEquals(12, analyzerPool.desiredAnalyzerCount)
        assertEquals(1, analyzerPool.analyzers.size)
        assertEquals(3, analyzerPool.analyzers[0].analyze(1, 2))
    }

    private class TestAnalyzer : Analyzer<Int, Int, Int> {
        override val name: String = "TestAnalyzer"
        override suspend fun analyze(data: Int, state: Int): Int = data + state
    }
}
