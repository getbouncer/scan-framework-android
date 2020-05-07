@file:JvmName("Coroutine")
package com.getbouncer.scan.framework.util

import kotlin.coroutines.Continuation
import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext

@JvmOverloads
fun <R> getContinuation(
    onFinished: ContinuationConsumer<R?, Throwable?>,
    executeOn: CoroutineDispatcher = Dispatchers.Default,
    callbackOn: CoroutineDispatcher = Dispatchers.Main
): Continuation<R> {
    return object : Continuation<R> {
        override val context: CoroutineContext = executeOn
        override fun resumeWith(result: Result<R>) {
            runBlocking {
                withContext(callbackOn) {
                    onFinished.accept(result.getOrNull(), result.exceptionOrNull())
                }
            }
        }
    }
}

/**
 * This is a local version of BiConsumer which allows use at API 21 instead of 24.
 */
@FunctionalInterface
interface ContinuationConsumer<T, U> {

    /**
     * Performs this operation on the given arguments.
     *
     * @param t the first input argument
     * @param u the second input argument
     */
    fun accept(t: T, u: U)
}
