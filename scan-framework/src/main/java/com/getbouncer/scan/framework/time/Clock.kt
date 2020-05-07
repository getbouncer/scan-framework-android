package com.getbouncer.scan.framework.time

import androidx.annotation.RestrictTo
import androidx.annotation.VisibleForTesting
import java.util.concurrent.atomic.AtomicLong

class AtomicClockMark(initialValue: ClockMark? = null) {
    private val originMarkNanos = AtomicLong(initialValue?.originMark?.inNanoseconds ?: Long.MIN_VALUE)

    fun setFirstTime(newValue: ClockMark) =
        originMarkNanos.compareAndSet(Long.MIN_VALUE, newValue.originMark.inNanoseconds)

    fun getAndSet(newValue: ClockMark): ClockMark? {
        val originMarkNanos = this.originMarkNanos.getAndSet(newValue.originMark.inNanoseconds)
        return if (originMarkNanos == Long.MIN_VALUE) {
            null
        } else {
            ClockMark(originMarkNanos.nanoseconds)
        }
    }

    fun get(): ClockMark? {
        val originMarkNanos = this.originMarkNanos.get()
        return if (originMarkNanos == Long.MIN_VALUE) {
            null
        } else {
            ClockMark(originMarkNanos.nanoseconds)
        }
    }

    fun setNow() {
        originMarkNanos.set(Clock.markNow().originMark.inNanoseconds)
    }

    fun clear() = originMarkNanos.set(Long.MIN_VALUE)
}

object Clock {
    fun markNow(): ClockMark = ClockMark(System.nanoTime().nanoseconds)

    @RestrictTo(RestrictTo.Scope.TESTS)
    @VisibleForTesting(otherwise = VisibleForTesting.NONE)
    fun markFromDuration(duration: Duration) = ClockMark(duration)
}

class ClockMark internal constructor(internal val originMark: Duration) {
    fun elapsedSince(): Duration = (System.nanoTime() - originMark.inNanoseconds).nanoseconds

    fun inMillisecondsSinceEpoch(): Long = System.currentTimeMillis() - elapsedSince().inMilliseconds.toLong()

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is ClockMark) return false

        if (originMark != other.originMark) return false

        return true
    }

    override fun hashCode(): Int {
        return originMark.hashCode()
    }

    override fun toString(): String {
        return "ClockMark(originMark=$originMark)"
    }
}

inline fun <T> measureTimeWithResult(block: () -> T): Pair<Duration, T> {
    val mark = Clock.markNow()
    val result = block()
    return mark.elapsedSince() to result
}

inline fun measureTime(block: () -> Unit): Duration {
    val mark = Clock.markNow()
    block()
    return mark.elapsedSince()
}
