package com.getbouncer.scan.framework.util

import androidx.test.filters.SmallTest
import com.getbouncer.scan.framework.time.milliseconds
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runBlockingTest
import org.junit.Test

class RetryTest {

    @Test
    @SmallTest
    @ExperimentalCoroutinesApi
    fun retry_succeedsFirst() = runBlockingTest {
        var executions = 0

        assertEquals(1, retry(1.milliseconds) {
            executions++
            1
        })
        assertEquals(1, executions)
    }

    @Test
    @SmallTest
    @ExperimentalCoroutinesApi
    fun retry_succeedsSecond() = runBlockingTest {
        var executions = 0

        assertEquals(1, retry(1.milliseconds) {
            executions++
            if (executions == 2) {
                1
            } else {
                throw RuntimeException()
            }
        })
        assertEquals(2, executions)
    }

    @Test
    @SmallTest
    @ExperimentalCoroutinesApi
    fun retry_fails() = runBlockingTest {
        var executions = 0

        assertFailsWith<RuntimeException> {
            retry<Int>(1.milliseconds) {
                executions++
                throw RuntimeException()
            }
        }
        assertEquals(3, executions)
    }
}
