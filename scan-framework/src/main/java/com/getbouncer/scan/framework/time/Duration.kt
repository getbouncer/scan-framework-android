package com.getbouncer.scan.framework.time

/**
 * Since kotlin time is still experimental, implement our own version for utility.
 */
sealed class Duration : Comparable<Duration> {

    companion object {
        val ZERO: Duration = DurationNanoseconds(0)
        val INFINITE: Duration = DurationInfinitePositive
        val NEGATIVE_INFINITE: Duration = DurationInfiniteNegative
    }

    abstract val inSeconds: Float

    abstract val inMilliseconds: Float

    abstract val inMicroseconds: Float

    abstract val inNanoseconds: Long

    override fun compareTo(other: Duration): Int = inNanoseconds.compareTo(other.inNanoseconds)

    override fun equals(other: Any?): Boolean =
        if (other is Duration) inNanoseconds == other.inNanoseconds else false

    override fun hashCode(): Int = inNanoseconds.toInt()

    override fun toString(): String {
        return "Duration($inNanoseconds nanoseconds)"
    }

    open operator fun plus(other: Duration): Duration = DurationNanoseconds(inNanoseconds + other.inNanoseconds)

    open operator fun minus(other: Duration): Duration = DurationNanoseconds(inNanoseconds - other.inNanoseconds)

    open operator fun div(denominator: Int): Duration = DurationNanoseconds(inNanoseconds / denominator)

    open operator fun unaryMinus(): Duration = DurationNanoseconds(-inNanoseconds)
}

private abstract class DurationInfinite : Duration() {
    override operator fun plus(other: Duration): Duration = this
    override operator fun minus(other: Duration): Duration = this
    override operator fun div(denominator: Int): Duration = this
}

private object DurationInfinitePositive : DurationInfinite() {
    override val inSeconds: Float = Float.POSITIVE_INFINITY
    override val inMilliseconds: Float = Float.POSITIVE_INFINITY
    override val inMicroseconds: Float = Float.POSITIVE_INFINITY
    override val inNanoseconds: Long = Long.MAX_VALUE

    override fun toString(): String {
        return "Duration(INFINITE)"
    }

    override operator fun unaryMinus(): Duration = DurationInfiniteNegative
}

private object DurationInfiniteNegative : DurationInfinite() {
    override val inSeconds: Float = Float.NEGATIVE_INFINITY
    override val inMilliseconds: Float = Float.NEGATIVE_INFINITY
    override val inMicroseconds: Float = Float.NEGATIVE_INFINITY
    override val inNanoseconds: Long = Long.MIN_VALUE

    override fun toString(): String {
        return "Duration(NEGATIVE_INFINITE)"
    }

    override operator fun unaryMinus(): Duration = DurationInfiniteNegative
}

private class DurationNanoseconds(private val nanoseconds: Long) : Duration() {
    override val inSeconds get(): Float = inMilliseconds / 1000
    override val inMilliseconds get(): Float = inMicroseconds / 1000
    override val inMicroseconds get(): Float = inNanoseconds.toFloat() / 1000
    override val inNanoseconds get(): Long = nanoseconds

    companion object {
        fun fromSeconds(seconds: Double) = fromMilliseconds(seconds * 1000)
        fun fromMilliseconds(milliseconds: Double) = fromMicroseconds(milliseconds * 1000)
        fun fromMicroseconds(microseconds: Double) = fromNanoseconds((microseconds * 1000).toLong())
        fun fromNanoseconds(nanoseconds: Long) = DurationNanoseconds(nanoseconds)
    }
}

val Int.seconds get(): Duration = this.toDouble().seconds
val Int.milliseconds get(): Duration = this.toDouble().milliseconds
val Int.microseconds get(): Duration = this.toDouble().microseconds
val Int.nanoseconds get(): Duration = this.toLong().nanoseconds

val Long.seconds get(): Duration = this.toDouble().seconds
val Long.milliseconds get(): Duration = this.toDouble().milliseconds
val Long.microseconds get(): Duration = this.toDouble().microseconds
val Long.nanoseconds get(): Duration = DurationNanoseconds.fromNanoseconds(this)

val Double.seconds get(): Duration = DurationNanoseconds.fromSeconds(this)
val Double.milliseconds get(): Duration = DurationNanoseconds.fromMilliseconds(this)
val Double.microseconds get(): Duration = DurationNanoseconds.fromMicroseconds(this)
val Double.nanoseconds get(): Duration = this.toLong().nanoseconds

fun min(duration1: Duration, duration2: Duration): Duration =
    when {
        duration1 is DurationInfinitePositive -> duration2
        duration1 is DurationInfiniteNegative -> duration1
        duration2 is DurationInfinitePositive -> duration1
        duration2 is DurationInfiniteNegative -> duration2
        else -> kotlin.math.min(duration1.inNanoseconds, duration2.inNanoseconds).nanoseconds
    }

fun max(duration1: Duration, duration2: Duration): Duration =
    when {
        duration1 is DurationInfinitePositive -> duration1
        duration1 is DurationInfiniteNegative -> duration2
        duration2 is DurationInfinitePositive -> duration2
        duration2 is DurationInfiniteNegative -> duration1
        else -> kotlin.math.max(duration1.inNanoseconds, duration2.inNanoseconds).nanoseconds
    }
