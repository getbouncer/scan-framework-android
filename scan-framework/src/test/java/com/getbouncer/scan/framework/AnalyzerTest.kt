package com.getbouncer.scan.framework

import androidx.test.filters.SmallTest
import kotlin.test.assertEquals
import kotlinx.coroutines.runBlocking
import org.junit.Test

class AnalyzerTest {

    @Test
    @SmallTest
    fun analyzerPoolCreateNormally() {
        class TestAnalyzerFactory : AnalyzerFactory<TestAnalyzer> {
            override val isThreadSafe: Boolean = true
            override suspend fun newInstance(): TestAnalyzer? = TestAnalyzer()
        }

        val analyzerPool = runBlocking { AnalyzerPool.Factory(
            analyzerFactory = TestAnalyzerFactory(),
            desiredAnalyzerCount = 12
        ).buildAnalyzerPool() }

        assertEquals(12, analyzerPool.desiredAnalyzerCount)
        assertEquals(12, analyzerPool.analyzers.size)
        assertEquals(3, runBlocking { analyzerPool.analyzers[0].analyze(1, 2) })
    }

    @Test
    @SmallTest
    fun analyzerPoolCreateFailure() {
        class TestAnalyzerFactory : AnalyzerFactory<TestAnalyzer> {
            override val isThreadSafe: Boolean = true
            override suspend fun newInstance(): TestAnalyzer? = null
        }

        val analyzerPool = runBlocking { AnalyzerPool.Factory(
            analyzerFactory = TestAnalyzerFactory(),
            desiredAnalyzerCount = 12
        ).buildAnalyzerPool() }

        assertEquals(12, analyzerPool.desiredAnalyzerCount)
        assertEquals(0, analyzerPool.analyzers.size)
    }

    @Test
    @SmallTest
    fun analyzerPoolSingleThreaded() {
        class TestAnalyzerFactory : AnalyzerFactory<TestAnalyzer> {
            override val isThreadSafe: Boolean = false
            override suspend fun newInstance(): TestAnalyzer? = TestAnalyzer()
        }

        val analyzerPool = runBlocking { AnalyzerPool.Factory(
            analyzerFactory = TestAnalyzerFactory(),
            desiredAnalyzerCount = 12
        ).buildAnalyzerPool() }

        assertEquals(12, analyzerPool.desiredAnalyzerCount)
        assertEquals(1, analyzerPool.analyzers.size)
        assertEquals(3, runBlocking { analyzerPool.analyzers[0].analyze(1, 2) })
    }

    private class TestAnalyzer : Analyzer<Int, Int, Int> {
        override val name: String = "TestAnalyzer"
        override suspend fun analyze(data: Int, state: Int): Int = data + state
    }
}
