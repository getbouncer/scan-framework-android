package com.getbouncer.scan.framework.util

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * A class that memoizes the result of a suspend function. Only one coroutine will ever perform the
 * work in the suspend function, others will suspend until a result is available, and then return
 * that result.
 */
class MemoizeSuspend0<out Result>(private val function: suspend () -> Result) {
    private val initializeMutex = Mutex()

    private object UNINITIALIZED_VALUE
    @Volatile private var value: Any? = UNINITIALIZED_VALUE

    suspend fun invoke(): Result = initializeMutex.withLock {
        if (value == UNINITIALIZED_VALUE) {
            value = function()
        }
        @Suppress("UNCHECKED_CAST") (value as Result)
    }
}

/**
 * A class that memoizes the result of a suspend function. Only one coroutine will ever perform the
 * work in the suspend function, others will suspend until a result is available, and then return
 * that result.
 */
class MemoizeSuspend1<in Input, out Result>(private val function: suspend (Input) -> Result) {
    private val lookupMutex = Mutex()

    private val values = mutableMapOf<Input, Result>()
    private val mutexes = mutableMapOf<Input, Mutex>()

    private suspend fun getMutex(input: Input): Mutex = lookupMutex.withLock {
        mutexes.getOrPut(input) { Mutex() }
    }

    suspend fun invoke(input: Input): Result = getMutex(input).withLock {
        values.getOrPut(input) { function(input) }
    }
}

/**
 * A class that memoizes the result of a suspend function. Only one coroutine will ever perform the
 * work in the suspend function, others will suspend until a result is available, and then return
 * that result.
 */
class MemoizeSuspend2<in Input1, in Input2, out Result>(private val function: suspend (Input1, Input2) -> Result) {
    private val lookupMutex = Mutex()

    private val values = mutableMapOf<Pair<Input1, Input2>, Result>()
    private val mutexes = mutableMapOf<Pair<Input1, Input2>, Mutex>()

    private suspend fun getMutex(input1: Input1, input2: Input2): Mutex = lookupMutex.withLock {
        mutexes.getOrPut(input1 to input2) { Mutex() }
    }

    suspend fun invoke(input1: Input1, input2: Input2): Result = getMutex(input1, input2).withLock {
        values.getOrPut(input1 to input2) { function(input1, input2) }
    }
}

/**
 * A class that memoizes the result of a function. This method is threadsafe. Only one thread will
 * ever invoke the backing function, other threads will block until a result is available, and then
 * return that result.
 */
class Memoize0<out Result>(private val function: () -> Result): () -> Result {
    private object UNINITIALIZED_VALUE
    @Volatile private var value: Any? = UNINITIALIZED_VALUE

    @Synchronized
    override fun invoke(): Result {
        if (value == UNINITIALIZED_VALUE) {
            value = function()
        }
        @Suppress("UNCHECKED_CAST") return (value as Result)
    }
}

/**
 * A class that memoizes the result of a function. This method is threadsafe. Only one thread will
 * ever invoke the backing function, other threads will block until a result is available, and then
 * return that result.
 */
class Memoize1<in Input, out Result>(private val function: (Input) -> Result): (Input) -> Result {
    private val values = mutableMapOf<Input, Result>()

    @Synchronized
    override fun invoke(input: Input): Result = values.getOrPut(input) { function(input) }
}

/**
 * A class that memoizes the result of a function. This method is threadsafe. Only one thread will
 * ever invoke the backing function, other threads will block until a result is available, and then
 * return that result.
 */
class Memoize2<in Input1, in Input2, out Result>(private val function: (Input1, Input2) -> Result): (Input1, Input2) -> Result {
    private val values = mutableMapOf<Pair<Input1, Input2>, Result>()
    private val locks = mutableMapOf<Pair<Input1, Input2>, Any>()

    @Synchronized
    private fun getLock(input1: Input1, input2: Input2): Any =
        locks.getOrPut(input1 to input2) { Object() }

    override fun invoke(input1: Input1, input2: Input2): Result {
        val lock = getLock(input1, input2)
        return synchronized(lock) {
            values.getOrPut(input1 to input2) { function(input1, input2) }
        }
    }
}

fun <Result> (() -> Result).memoize() = Memoize0(this)

fun <Result> (suspend () -> Result).memoize() = MemoizeSuspend0(this)
fun <Input, Result> (suspend (Input) -> Result).memoize() = MemoizeSuspend1(this)
fun <Input1, Input2, Result> (suspend (Input1, Input2) -> Result).memoize() = MemoizeSuspend2(this)
