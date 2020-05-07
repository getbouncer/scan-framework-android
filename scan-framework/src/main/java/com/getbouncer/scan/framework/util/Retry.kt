package com.getbouncer.scan.framework.util

import androidx.annotation.RestrictTo
import com.getbouncer.scan.framework.time.Duration
import kotlinx.coroutines.delay

private const val DEFAULT_RETRIES = 3

@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
suspend fun <T> retry(
    retryDelay: Duration,
    times: Int = DEFAULT_RETRIES,
    task: suspend () -> T
): T {
    var exception: Throwable? = null
    for (attempt in 1..times) {
        try {
            return task()
        } catch (t: Throwable) {
            exception = t
            if (attempt < times) {
                delay(retryDelay.inMilliseconds.toLong())
            }
        }
    }

    if (exception != null) {
        throw exception
    } else {
        // This code should never be reached
        throw UnexpectedRetryException()
    }
}

@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
class UnexpectedRetryException : Exception()
