package com.getbouncer.scan.framework.util

import androidx.test.filters.SmallTest
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runBlockingTest
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

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
    fun cacheResult0wrapper_onlyRunsOnce() {
        var functionRunCount = 0

        val testFunction = cacheResult<Boolean> {
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
    fun cacheResult0_onlyRunsOnce() {
        var functionRunCount = 0

        val testFunction = {
            functionRunCount++
            true
        }.cachedResult()

        assertTrue { testFunction.invoke() }
        assertTrue { testFunction.invoke() }
        assertTrue { testFunction.invoke() }

        assertEquals(1, functionRunCount)
    }

    @Test
    @SmallTest
    @ExperimentalCoroutinesApi
    fun cacheResultSuspend0wrapper_onlyRunsOnce() = runBlockingTest {
        var functionRunCount = 0

        val testFunction = cacheResultSuspend<Boolean> {
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
    fun cachedResultSuspend0_onlyRunsOnce() = runBlockingTest {
        var functionRunCount = 0

        val testFunction = suspend {
            functionRunCount++
            delay(100)
            true
        }.cachedResultSuspend()

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
    fun cacheResult1wrapper_onlyRunsOnce() {
        var functionRunCount = 0

        val testFunction = cacheResult { input: Int ->
            functionRunCount++
            input > 0
        }

        assertTrue { testFunction.invoke(1) }
        assertTrue { testFunction.invoke(1) }
        assertTrue { testFunction.invoke(1) }
        assertTrue { testFunction.invoke(2) }
        assertTrue { testFunction.invoke(2) }
        assertTrue { testFunction.invoke(1) }
        assertTrue { testFunction.invoke(-1) }

        assertEquals(1, functionRunCount)
    }

    @Test
    @SmallTest
    fun cachedResult1_onlyRunsOnce() {
        var functionRunCount = 0

        val testFunction = { input: Int ->
            functionRunCount++
            input > 0
        }.cachedResult()

        assertTrue { testFunction.invoke(1) }
        assertTrue { testFunction.invoke(1) }
        assertTrue { testFunction.invoke(1) }
        assertTrue { testFunction.invoke(2) }
        assertTrue { testFunction.invoke(2) }
        assertTrue { testFunction.invoke(1) }
        assertTrue { testFunction.invoke(-1) }

        assertEquals(1, functionRunCount)
    }

    @Test
    @SmallTest
    @ExperimentalCoroutinesApi
    fun cacheResultSuspend1wrapper_onlyRunsOnce() = runBlockingTest {
        var functionRunCount = 0

        val testFunction = cacheResultSuspend { input: Int ->
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
        val result7 = testFunction.invoke(-1)

        assertTrue { result1 }
        assertTrue { result2 }
        assertTrue { result3 }
        assertTrue { result4 }
        assertTrue { result5 }
        assertTrue { result6 }
        assertTrue { result7 }

        assertEquals(1, functionRunCount)
    }

    @Test
    @SmallTest
    @ExperimentalCoroutinesApi
    fun cachedResultSuspend1_onlyRunsOnce() = runBlockingTest {
        var functionRunCount = 0

        val testFunction: suspend (Int) -> Boolean = { input ->
            functionRunCount++
            delay(100)
            input > 0
        }

        val cachedResultFunction = testFunction.cachedResultSuspend()

        val result1 = cachedResultFunction.invoke(1)
        val result2 = cachedResultFunction.invoke(1)
        val result3 = cachedResultFunction.invoke(1)
        val result4 = cachedResultFunction.invoke(2)
        val result5 = cachedResultFunction.invoke(2)
        val result6 = cachedResultFunction.invoke(1)
        val result7 = cachedResultFunction.invoke(-1)

        assertTrue { result1 }
        assertTrue { result2 }
        assertTrue { result3 }
        assertTrue { result4 }
        assertTrue { result5 }
        assertTrue { result6 }
        assertTrue { result7 }

        assertEquals(1, functionRunCount)
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

    @Test
    @SmallTest
    fun cacheResult2wrapper_onlyRunsOnce() {
        var functionRunCount = 0

        val testFunction = cacheResult { input1: Int, input2: Int ->
            functionRunCount++
            input1 > 0 && input2 > 0
        }

        assertTrue { testFunction.invoke(1, 2) }
        assertTrue { testFunction.invoke(1, 2) }
        assertTrue { testFunction.invoke(1, 2) }
        assertTrue { testFunction.invoke(1, 3) }
        assertTrue { testFunction.invoke(2, 2) }
        assertTrue { testFunction.invoke(4, 5) }
        assertTrue { testFunction.invoke(1, -1) }

        assertEquals(1, functionRunCount)
    }

    @Test
    @SmallTest
    fun cachedResult2_onlyRunsOnce() {
        var functionRunCount = 0

        val testFunction = { input1: Int, input2: Int ->
            functionRunCount++
            input1 > 0 && input2 > 0
        }.cachedResult()

        assertTrue { testFunction.invoke(1, 2) }
        assertTrue { testFunction.invoke(1, 2) }
        assertTrue { testFunction.invoke(1, 2) }
        assertTrue { testFunction.invoke(1, 3) }
        assertTrue { testFunction.invoke(2, 2) }
        assertTrue { testFunction.invoke(4, 5) }
        assertTrue { testFunction.invoke(1, -1) }

        assertEquals(1, functionRunCount)
    }

    @Test
    @SmallTest
    @ExperimentalCoroutinesApi
    fun cacheResultSuspend2wrapper_onlyRunsOnce() = runBlockingTest {
        var functionRunCount = 0

        val testFunction = cacheResultSuspend { input1: Int, input2: Int ->
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
        val result7 = testFunction.invoke(1, -1)

        assertTrue { result1 }
        assertTrue { result2 }
        assertTrue { result3 }
        assertTrue { result4 }
        assertTrue { result5 }
        assertTrue { result6 }
        assertTrue { result7 }

        assertEquals(1, functionRunCount)
    }

    @Test
    @SmallTest
    @ExperimentalCoroutinesApi
    fun cachedResultSuspend2_onlyRunsOnce() = runBlockingTest {
        var functionRunCount = 0

        val testFunction: suspend (Int, Int) -> Boolean = { input1, input2 ->
            functionRunCount++
            delay(100)
            input1 > 0 && input2 > 0
        }

        val cachedResultFunction = testFunction.cachedResultSuspend()

        val result1 = cachedResultFunction.invoke(1, 2)
        val result2 = cachedResultFunction.invoke(1, 2)
        val result3 = cachedResultFunction.invoke(1, 2)
        val result4 = cachedResultFunction.invoke(1, 3)
        val result5 = cachedResultFunction.invoke(2, 2)
        val result6 = cachedResultFunction.invoke(4, 5)
        val result7 = cachedResultFunction.invoke(1, -1)

        assertTrue { result1 }
        assertTrue { result2 }
        assertTrue { result3 }
        assertTrue { result4 }
        assertTrue { result5 }
        assertTrue { result6 }
        assertTrue { result7 }

        assertEquals(1, functionRunCount)
    }

    @Test
    @SmallTest
    fun memoize3wrapper_onlyRunsOnce() {
        var functionRunCount = 0

        val testFunction = memoize { input1: Int, input2: Int, input3: Int ->
            functionRunCount++
            input1 > 0 && input2 > 0 && input3 > 0
        }

        assertTrue { testFunction.invoke(1, 2, 3) }
        assertTrue { testFunction.invoke(1, 2, 3) }
        assertTrue { testFunction.invoke(1, 2, 3) }
        assertTrue { testFunction.invoke(1, 3, 4) }
        assertTrue { testFunction.invoke(2, 2, 5) }
        assertTrue { testFunction.invoke(4, 5, 6) }
        assertTrue { testFunction.invoke(1, 2, 7) }
        assertTrue { testFunction.invoke(1, 2, 3) }

        assertEquals(5, functionRunCount)
    }

    @Test
    @SmallTest
    fun memoize3_onlyRunsOnce() {
        var functionRunCount = 0

        val testFunction = { input1: Int, input2: Int, input3: Int ->
            functionRunCount++
            input1 > 0 && input2 > 0 && input3 > 0
        }.memoized()

        assertTrue { testFunction.invoke(1, 2, 3) }
        assertTrue { testFunction.invoke(1, 2, 3) }
        assertTrue { testFunction.invoke(1, 2, 3) }
        assertTrue { testFunction.invoke(1, 3, 4) }
        assertTrue { testFunction.invoke(2, 2, 5) }
        assertTrue { testFunction.invoke(4, 5, 6) }
        assertTrue { testFunction.invoke(1, 2, 7) }
        assertTrue { testFunction.invoke(1, 2, 3) }

        assertEquals(5, functionRunCount)
    }

    @Test
    @SmallTest
    @ExperimentalCoroutinesApi
    fun memoizeSuspend3wrapper_onlyRunsOnce() = runBlockingTest {
        var functionRunCount = 0

        val testFunction = memoizeSuspend { input1: Int, input2: Int, input3: Int ->
            functionRunCount++
            delay(100)
            input1 > 0 && input2 > 0 && input3 > 0
        }

        val result1 = testFunction.invoke(1, 2, 3)
        val result2 = testFunction.invoke(1, 2, 3)
        val result3 = testFunction.invoke(1, 2, 3)
        val result4 = testFunction.invoke(1, 3, 4)
        val result5 = testFunction.invoke(2, 2, 5)
        val result6 = testFunction.invoke(4, 5, 6)
        val result7 = testFunction.invoke(1, 2, 7)
        val result8 = testFunction.invoke(1, 2, 3)

        assertTrue { result1 }
        assertTrue { result2 }
        assertTrue { result3 }
        assertTrue { result4 }
        assertTrue { result5 }
        assertTrue { result6 }
        assertTrue { result7 }
        assertTrue { result8 }

        assertEquals(5, functionRunCount)
    }

    @Test
    @SmallTest
    @ExperimentalCoroutinesApi
    fun memoizeSuspend3_onlyRunsOnce() = runBlockingTest {
        var functionRunCount = 0

        val testFunction: suspend (Int, Int, Int) -> Boolean = { input1, input2, input3 ->
            functionRunCount++
            delay(100)
            input1 > 0 && input2 > 0 && input3 > 0
        }

        val memoizedFunction = testFunction.memoizedSuspend()

        val result1 = memoizedFunction.invoke(1, 2, 3)
        val result2 = memoizedFunction.invoke(1, 2, 3)
        val result3 = memoizedFunction.invoke(1, 2, 3)
        val result4 = memoizedFunction.invoke(1, 3, 4)
        val result5 = memoizedFunction.invoke(2, 2, 5)
        val result6 = memoizedFunction.invoke(4, 5, 6)
        val result7 = memoizedFunction.invoke(1, 2, 7)
        val result8 = memoizedFunction.invoke(1, 2, 3)

        assertTrue { result1 }
        assertTrue { result2 }
        assertTrue { result3 }
        assertTrue { result4 }
        assertTrue { result5 }
        assertTrue { result6 }
        assertTrue { result7 }
        assertTrue { result8 }

        assertEquals(5, functionRunCount)
    }

    @Test
    @SmallTest
    fun cacheFirstResult0wrapper_onlyRunsOnce() {
        var functionRunCount = 0

        val testFunction = cacheFirstResult<Boolean> {
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
    fun cacheFirstResult0_onlyRunsOnce() {
        var functionRunCount = 0

        val testFunction = {
            functionRunCount++
            true
        }.cachedFirstResult()

        assertTrue { testFunction.invoke() }
        assertTrue { testFunction.invoke() }
        assertTrue { testFunction.invoke() }

        assertEquals(1, functionRunCount)
    }

    @Test
    @SmallTest
    @ExperimentalCoroutinesApi
    fun cacheFirstResultSuspend0wrapper_onlyRunsOnce() = runBlockingTest {
        var functionRunCount = 0

        val testFunction = cacheFirstResultSuspend<Boolean> {
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
    fun cachedFirstResultSuspend0_onlyRunsOnce() = runBlockingTest {
        var functionRunCount = 0

        val testFunction = suspend {
            functionRunCount++
            delay(100)
            true
        }.cachedFirstResultSuspend()

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
    fun cacheFirstResult1wrapper_onlyRunsOnce() {
        var functionRunCount = 0

        val testFunction = cacheFirstResult { input: Int ->
            functionRunCount++
            input > 0
        }

        assertTrue { testFunction.invoke(1) }
        assertTrue { testFunction.invoke(1) }
        assertTrue { testFunction.invoke(1) }
        assertTrue { testFunction.invoke(2) }
        assertTrue { testFunction.invoke(2) }
        assertTrue { testFunction.invoke(1) }
        assertTrue { testFunction.invoke(-1) }

        assertEquals(1, functionRunCount)
    }

    @Test
    @SmallTest
    fun cachedFirstResult1_onlyRunsOnce() {
        var functionRunCount = 0

        val testFunction = { input: Int ->
            functionRunCount++
            input > 0
        }.cachedFirstResult()

        assertTrue { testFunction.invoke(1) }
        assertTrue { testFunction.invoke(1) }
        assertTrue { testFunction.invoke(1) }
        assertTrue { testFunction.invoke(2) }
        assertTrue { testFunction.invoke(2) }
        assertTrue { testFunction.invoke(1) }
        assertTrue { testFunction.invoke(-1) }

        assertEquals(1, functionRunCount)
    }

    @Test
    @SmallTest
    @ExperimentalCoroutinesApi
    fun cacheFirstResultSuspend1wrapper_onlyRunsOnce() = runBlockingTest {
        var functionRunCount = 0

        val testFunction = cacheFirstResultSuspend { input: Int ->
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
        val result7 = testFunction.invoke(-1)

        assertTrue { result1 }
        assertTrue { result2 }
        assertTrue { result3 }
        assertTrue { result4 }
        assertTrue { result5 }
        assertTrue { result6 }
        assertTrue { result7 }

        assertEquals(1, functionRunCount)
    }

    @Test
    @SmallTest
    @ExperimentalCoroutinesApi
    fun cachedFirstResultSuspend1_onlyRunsOnce() = runBlockingTest {
        var functionRunCount = 0

        val testFunction: suspend (Int) -> Boolean = { input ->
            functionRunCount++
            delay(100)
            input > 0
        }

        val cachedFirstResultFunction = testFunction.cachedFirstResultSuspend()

        val result1 = cachedFirstResultFunction.invoke(1)
        val result2 = cachedFirstResultFunction.invoke(1)
        val result3 = cachedFirstResultFunction.invoke(1)
        val result4 = cachedFirstResultFunction.invoke(2)
        val result5 = cachedFirstResultFunction.invoke(2)
        val result6 = cachedFirstResultFunction.invoke(1)
        val result7 = cachedFirstResultFunction.invoke(-1)

        assertTrue { result1 }
        assertTrue { result2 }
        assertTrue { result3 }
        assertTrue { result4 }
        assertTrue { result5 }
        assertTrue { result6 }
        assertTrue { result7 }

        assertEquals(1, functionRunCount)
    }

    @Test
    @SmallTest
    fun cacheFirstResult2wrapper_onlyRunsOnce() {
        var functionRunCount = 0

        val testFunction = cacheFirstResult { input1: Int, input2: Int ->
            functionRunCount++
            input1 > 0 && input2 > 0
        }

        assertTrue { testFunction.invoke(1, 2) }
        assertTrue { testFunction.invoke(1, 2) }
        assertTrue { testFunction.invoke(1, 2) }
        assertTrue { testFunction.invoke(1, 3) }
        assertTrue { testFunction.invoke(2, 2) }
        assertTrue { testFunction.invoke(4, 5) }
        assertTrue { testFunction.invoke(1, -1) }

        assertEquals(1, functionRunCount)
    }

    @Test
    @SmallTest
    fun cachedFirstResult2_onlyRunsOnce() {
        var functionRunCount = 0

        val testFunction = { input1: Int, input2: Int ->
            functionRunCount++
            input1 > 0 && input2 > 0
        }.cachedFirstResult()

        assertTrue { testFunction.invoke(1, 2) }
        assertTrue { testFunction.invoke(1, 2) }
        assertTrue { testFunction.invoke(1, 2) }
        assertTrue { testFunction.invoke(1, 3) }
        assertTrue { testFunction.invoke(2, 2) }
        assertTrue { testFunction.invoke(4, 5) }
        assertTrue { testFunction.invoke(1, -1) }

        assertEquals(1, functionRunCount)
    }

    @Test
    @SmallTest
    @ExperimentalCoroutinesApi
    fun cacheFirstResultSuspend2wrapper_onlyRunsOnce() = runBlockingTest {
        var functionRunCount = 0

        val testFunction = cacheFirstResultSuspend { input1: Int, input2: Int ->
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
        val result7 = testFunction.invoke(1, -1)

        assertTrue { result1 }
        assertTrue { result2 }
        assertTrue { result3 }
        assertTrue { result4 }
        assertTrue { result5 }
        assertTrue { result6 }
        assertTrue { result7 }

        assertEquals(1, functionRunCount)
    }

    @Test
    @SmallTest
    @ExperimentalCoroutinesApi
    fun cachedFirstResultSuspend2_onlyRunsOnce() = runBlockingTest {
        var functionRunCount = 0

        val testFunction: suspend (Int, Int) -> Boolean = { input1, input2 ->
            functionRunCount++
            delay(100)
            input1 > 0 && input2 > 0
        }

        val cachedFirstResultFunction = testFunction.cachedFirstResultSuspend()

        val result1 = cachedFirstResultFunction.invoke(1, 2)
        val result2 = cachedFirstResultFunction.invoke(1, 2)
        val result3 = cachedFirstResultFunction.invoke(1, 2)
        val result4 = cachedFirstResultFunction.invoke(1, 3)
        val result5 = cachedFirstResultFunction.invoke(2, 2)
        val result6 = cachedFirstResultFunction.invoke(4, 5)
        val result7 = cachedFirstResultFunction.invoke(1, -1)

        assertTrue { result1 }
        assertTrue { result2 }
        assertTrue { result3 }
        assertTrue { result4 }
        assertTrue { result5 }
        assertTrue { result6 }
        assertTrue { result7 }

        assertEquals(1, functionRunCount)
    }

    @Test
    @SmallTest
    fun cacheFirstResult3wrapper_onlyRunsOnce() {
        var functionRunCount = 0

        val testFunction = cacheFirstResult { input1: Int, input2: Int, input3: Int ->
            functionRunCount++
            input1 > 0 && input2 > 0 && input3 > 0
        }

        assertTrue { testFunction.invoke(1, 2, 3) }
        assertTrue { testFunction.invoke(1, 2, 3) }
        assertTrue { testFunction.invoke(1, 2, 3) }
        assertTrue { testFunction.invoke(1, 3, 4) }
        assertTrue { testFunction.invoke(2, 2, 5) }
        assertTrue { testFunction.invoke(4, 5, 6) }
        assertTrue { testFunction.invoke(1, 2, 7) }
        assertTrue { testFunction.invoke(1, 2, -1) }

        assertEquals(1, functionRunCount)
    }

    @Test
    @SmallTest
    fun cachedFirstResult3_onlyRunsOnce() {
        var functionRunCount = 0

        val testFunction = { input1: Int, input2: Int, input3: Int ->
            functionRunCount++
            input1 > 0 && input2 > 0 && input3 > 0
        }.cachedFirstResult()

        assertTrue { testFunction.invoke(1, 2, 3) }
        assertTrue { testFunction.invoke(1, 2, 3) }
        assertTrue { testFunction.invoke(1, 2, 3) }
        assertTrue { testFunction.invoke(1, 3, 4) }
        assertTrue { testFunction.invoke(2, 2, 5) }
        assertTrue { testFunction.invoke(4, 5, 6) }
        assertTrue { testFunction.invoke(1, 2, 7) }
        assertTrue { testFunction.invoke(1, 2, -1) }

        assertEquals(1, functionRunCount)
    }

    @Test
    @SmallTest
    @ExperimentalCoroutinesApi
    fun cacheFirstResultSuspend3wrapper_onlyRunsOnce() = runBlockingTest {
        var functionRunCount = 0

        val testFunction = cacheFirstResultSuspend { input1: Int, input2: Int, input3: Int ->
            functionRunCount++
            delay(100)
            input1 > 0 && input2 > 0 && input3 > 0
        }

        val result1 = testFunction.invoke(1, 2, 3)
        val result2 = testFunction.invoke(1, 2, 3)
        val result3 = testFunction.invoke(1, 2, 3)
        val result4 = testFunction.invoke(1, 3, 4)
        val result5 = testFunction.invoke(2, 2, 5)
        val result6 = testFunction.invoke(4, 5, 6)
        val result7 = testFunction.invoke(1, 2, 7)
        val result8 = testFunction.invoke(1, 2, -1)

        assertTrue { result1 }
        assertTrue { result2 }
        assertTrue { result3 }
        assertTrue { result4 }
        assertTrue { result5 }
        assertTrue { result6 }
        assertTrue { result7 }
        assertTrue { result8 }

        assertEquals(1, functionRunCount)
    }

    @Test
    @SmallTest
    @ExperimentalCoroutinesApi
    fun cachedFirstResultSuspend3_onlyRunsOnce() = runBlockingTest {
        var functionRunCount = 0

        val testFunction: suspend (Int, Int, Int) -> Boolean = { input1, input2, input3 ->
            functionRunCount++
            delay(100)
            input1 > 0 && input2 > 0 && input3 > 0
        }

        val cachedFirstResultFunction = testFunction.cachedFirstResultSuspend()

        val result1 = cachedFirstResultFunction.invoke(1, 2, 3)
        val result2 = cachedFirstResultFunction.invoke(1, 2, 3)
        val result3 = cachedFirstResultFunction.invoke(1, 2, 3)
        val result4 = cachedFirstResultFunction.invoke(1, 3, 4)
        val result5 = cachedFirstResultFunction.invoke(2, 2, 5)
        val result6 = cachedFirstResultFunction.invoke(4, 5, 6)
        val result7 = cachedFirstResultFunction.invoke(1, 2, 7)
        val result8 = cachedFirstResultFunction.invoke(1, 2, -1)

        assertTrue { result1 }
        assertTrue { result2 }
        assertTrue { result3 }
        assertTrue { result4 }
        assertTrue { result5 }
        assertTrue { result6 }
        assertTrue { result7 }
        assertTrue { result8 }

        assertEquals(1, functionRunCount)
    }
}
