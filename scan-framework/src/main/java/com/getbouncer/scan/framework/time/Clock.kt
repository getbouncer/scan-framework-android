package com.getbouncer.scan.framework.time

object Clock {
    fun markNow(): ClockMark = ClockMark()
}

class ClockMark internal constructor() {
    private val originMark = System.nanoTime()

    fun elapsedSince(): Duration = (System.nanoTime() - originMark).nanoseconds

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
        return "ClockMark($originMark)"
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
