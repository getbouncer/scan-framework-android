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
    @ExperimentalCoroutinesApi
    fun memoizeSuspend0_onlyRunsOnce() = runBlockingTest {
        var functionRunCount = 0

        val testFunction = suspend {
            functionRunCount++
            delay(100)
            true
        }.memoize()

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
    fun memoizeSuspend1_onlyRunsOnce() = runBlockingTest {
        var functionRunCount = 0

        val testFunction: suspend (Int) -> Boolean = {
            functionRunCount++
            delay(100)
            it > 0
        }

        val memoizedFunction = testFunction.memoize()

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
    @ExperimentalCoroutinesApi
    fun memoizeSuspend2_onlyRunsOnce() = runBlockingTest {
        var functionRunCount = 0

        val testFunction = MemoizeSuspend2(
            function = (Int, Int) -> Boolean {

            }
        )

        val result1 = testFunction.invoke()
        val result2 = testFunction.invoke()
        val result3 = testFunction.invoke()

        assertTrue { result1 }
        assertTrue { result2 }
        assertTrue { result3 }

        assertEquals(1, functionRunCount)
    }
}
