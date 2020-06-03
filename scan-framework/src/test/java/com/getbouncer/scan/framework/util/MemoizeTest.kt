package com.getbouncer.scan.framework.util

import androidx.test.filters.SmallTest
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runBlockingTest
import org.junit.Test

class MemoizeTest {

    @Test
    @SmallTest
    fun memoize0wrapper_onlyRunsOnce() {
        var functionRunCount = 0

        val testFunction = memoize<Boolean> {
            functionRunCount++
            true
        }

        assertTrue { testFunction.invoke() }
        assertTrue { testFunction.invoke() }
        assertTrue { testFunction.invoke() }

        assertEquals(1, functionRunCount)
    }

    @Test
    @SmallTest
    fun memoize0_onlyRunsOnce() {
        var functionRunCount = 0

        val testFunction = {
            functionRunCount++
            true
        }.memoized()

        assertTrue { testFunction.invoke() }
        assertTrue { testFunction.invoke() }
        assertTrue { testFunction.invoke() }

        assertEquals(1, functionRunCount)
    }

    @Test
    @SmallTest
    @ExperimentalCoroutinesApi
    fun memoizeSuspend0wrapper_onlyRunsOnce() = runBlockingTest {
        var functionRunCount = 0

        val testFunction = memoizeSuspend<Boolean> {
            functionRunCount++
            delay(100)
            true
        }

        val result1 = testFunction.invoke()
        val result2 = testFunction.invoke()
        val result3 = testFunction.invoke()

        assertTrue { result1 }
        assertTrue { result2 }
        assertTrue { result3 }

        assertEquals(1, functionRunCount)
    }

    @Test
    @SmallTest
    @ExperimentalCoroutinesApi
    fun memoizeSuspend0_onlyRunsOnce() = runBlockingTest {
        var functionRunCount = 0

        val testFunction = suspend {
            functionRunCount++
            delay(100)
            true
        }.memoizedSuspend()

        val result1 = testFunction.invoke()
        val result2 = testFunction.invoke()
        val result3 = testFunction.invoke()

        assertTrue { result1 }
        assertTrue { result2 }
        assertTrue { result3 }

        assertEquals(1, functionRunCount)
    }

    @Test
    @SmallTest
    fun memoize1wrapper_onlyRunsOnce() {
        var functionRunCount = 0

        val testFunction = memoize { input: Int ->
            functionRunCount++
            input > 0
        }

        assertTrue { testFunction.invoke(1) }
        assertTrue { testFunction.invoke(1) }
        assertTrue { testFunction.invoke(1) }
        assertTrue { testFunction.invoke(2) }
        assertTrue { testFunction.invoke(2) }
        assertTrue { testFunction.invoke(1) }

        assertEquals(2, functionRunCount)
    }

    @Test
    @SmallTest
    fun memoize1_onlyRunsOnce() {
        var functionRunCount = 0

        val testFunction = { input: Int ->
            functionRunCount++
            input > 0
        }.memoized()

        assertTrue { testFunction.invoke(1) }
        assertTrue { testFunction.invoke(1) }
        assertTrue { testFunction.invoke(1) }
        assertTrue { testFunction.invoke(2) }
        assertTrue { testFunction.invoke(2) }
        assertTrue { testFunction.invoke(1) }

        assertEquals(2, functionRunCount)
    }

    @Test
    @SmallTest
    @ExperimentalCoroutinesApi
    fun memoizeSuspend1wrapper_onlyRunsOnce() = runBlockingTest {
        var functionRunCount = 0

        val testFunction = memoizeSuspend { input: Int ->
            functionRunCount++
            delay(100)
            input > 0
        }

        val result1 = testFunction.invoke(1)
        val result2 = testFunction.invoke(1)
        val result3 = testFunction.invoke(1)
        val result4 = testFunction.invoke(2)
        val result5 = testFunction.invoke(2)
        val result6 = testFunction.invoke(1)

        assertTrue { result1 }
        assertTrue { result2 }
        assertTrue { result3 }
        assertTrue { result4 }
        assertTrue { result5 }
        assertTrue { result6 }

        assertEquals(2, functionRunCount)
    }

    @Test
    @SmallTest
    @ExperimentalCoroutinesApi
    fun memoizeSuspend1_onlyRunsOnce() = runBlockingTest {
        var functionRunCount = 0

        val testFunction: suspend (Int) -> Boolean = { input ->
            functionRunCount++
            delay(100)
            input > 0
        }

        val memoizedFunction = testFunction.memoizedSuspend()

        val result1 = memoizedFunction.invoke(1)
        val result2 = memoizedFunction.invoke(1)
        val result3 = memoizedFunction.invoke(1)
        val result4 = memoizedFunction.invoke(2)
        val result5 = memoizedFunction.invoke(2)
        val result6 = memoizedFunction.invoke(1)

        assertTrue { result1 }
        assertTrue { result2 }
        assertTrue { result3 }
        assertTrue { result4 }
        assertTrue { result5 }
        assertTrue { result6 }

        assertEquals(2, functionRunCount)
    }

    @Test
    @SmallTest
    fun memoize2wrapper_onlyRunsOnce() {
        var functionRunCount = 0

        val testFunction = memoize { input1: Int, input2: Int ->
            functionRunCount++
            input1 > 0 && input2 > 0
        }

        assertTrue { testFunction.invoke(1, 2) }
        assertTrue { testFunction.invoke(1, 2) }
        assertTrue { testFunction.invoke(1, 2) }
        assertTrue { testFunction.invoke(1, 3) }
        assertTrue { testFunction.invoke(2, 2) }
        assertTrue { testFunction.invoke(4, 5) }
        assertTrue { testFunction.invoke(1, 2) }

        assertEquals(4, functionRunCount)
    }

    @Test
    @SmallTest
    fun memoize2_onlyRunsOnce() {
        var functionRunCount = 0

        val testFunction = { input1: Int, input2: Int ->
            functionRunCount++
            input1 > 0 && input2 > 0
        }.memoized()

        assertTrue { testFunction.invoke(1, 2) }
        assertTrue { testFunction.invoke(1, 2) }
        assertTrue { testFunction.invoke(1, 2) }
        assertTrue { testFunction.invoke(1, 3) }
        assertTrue { testFunction.invoke(2, 2) }
        assertTrue { testFunction.invoke(4, 5) }
        assertTrue { testFunction.invoke(1, 2) }

        assertEquals(4, functionRunCount)
    }

    @Test
    @SmallTest
    @ExperimentalCoroutinesApi
    fun memoizeSuspend2wrapper_onlyRunsOnce() = runBlockingTest {
        var functionRunCount = 0

        val testFunction = memoizeSuspend { input1: Int, input2: Int ->
            functionRunCount++
            delay(100)
            input1 > 0 && input2 > 0
        }

        val result1 = testFunction.invoke(1, 2)
        val result2 = testFunction.invoke(1, 2)
        val result3 = testFunction.invoke(1, 2)
        val result4 = testFunction.invoke(1, 3)
        val result5 = testFunction.invoke(2, 2)
        val result6 = testFunction.invoke(4, 5)
        val result7 = testFunction.invoke(1, 2)

        assertTrue { result1 }
        assertTrue { result2 }
        assertTrue { result3 }
        assertTrue { result4 }
        assertTrue { result5 }
        assertTrue { result6 }
        assertTrue { result7 }

        assertEquals(4, functionRunCount)
    }

    @Test
    @SmallTest
    @ExperimentalCoroutinesApi
    fun memoizeSuspend2_onlyRunsOnce() = runBlockingTest {
        var functionRunCount = 0

        val testFunction: suspend (Int, Int) -> Boolean = { input1, input2 ->
            functionRunCount++
            delay(100)
            input1 > 0 && input2 > 0
        }

        val memoizedFunction = testFunction.memoizedSuspend()

        val result1 = memoizedFunction.invoke(1, 2)
        val result2 = memoizedFunction.invoke(1, 2)
        val result3 = memoizedFunction.invoke(1, 2)
        val result4 = memoizedFunction.invoke(1, 3)
        val result5 = memoizedFunction.invoke(2, 2)
        val result6 = memoizedFunction.invoke(4, 5)
        val result7 = memoizedFunction.invoke(1, 2)

        assertTrue { result1 }
        assertTrue { result2 }
        assertTrue { result3 }
        assertTrue { result4 }
        assertTrue { result5 }
        assertTrue { result6 }
        assertTrue { result7 }

        assertEquals(4, functionRunCount)
    }
}
