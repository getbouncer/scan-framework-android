package com.getbouncer.scan.framework.util

import androidx.test.filters.SmallTest
import com.getbouncer.scan.framework.time.milliseconds
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlinx.coroutines.runBlocking
import org.junit.Test

class RetryTest {

    @Test
    @SmallTest
    fun retry_succeedsFirst() {
        var executions = 0

        assertEquals(1, runBlocking { retry(1.milliseconds) {
            executions++
            1
        } })
        assertEquals(1, executions)
    }

    @Test
    @SmallTest
    fun retry_succeedsSecond() {
        var executions = 0

        assertEquals(1, runBlocking { retry(1.milliseconds) {
            executions++
            if (executions == 2) {
                1
            } else {
                throw RuntimeException()
            }
        } })
        assertEquals(2, executions)
    }

    @Test
    @SmallTest
    fun retry_fails() {
        var executions = 0

        assertFailsWith<RuntimeException> {
            runBlocking { retry(1.milliseconds) {
                executions++
                throw RuntimeException()
            } }
        }
        assertEquals(3, executions)
    }
}
