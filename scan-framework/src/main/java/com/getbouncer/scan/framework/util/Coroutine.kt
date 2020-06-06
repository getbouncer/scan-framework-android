@file:JvmName("Coroutine")
package com.getbouncer.scan.framework.util

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlin.coroutines.Continuation
import kotlin.coroutines.CoroutineContext

/**
 * A utility class for calling suspend functions from java. This transforms a suspend function into a
 */
abstract class JavaContinuation<in T>(runOn: CoroutineContext = Dispatchers.Default, private val listenOn: CoroutineContext = Dispatchers.Main) : Continuation<T> {
    abstract fun resume(value: T)
    abstract fun resumeWithException(exception: Throwable)
    override fun resumeWith(result: Result<T>) = result.fold(
        onSuccess = {
            runBlocking(listenOn) {
                resume(it)
            }
        },
        onFailure = {
            runBlocking(listenOn) {
                resumeWithException(it)
            }
        }
    )
    override val context: CoroutineContext = runOn
}
