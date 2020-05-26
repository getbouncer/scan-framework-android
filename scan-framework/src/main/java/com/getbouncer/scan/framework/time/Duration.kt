package com.getbouncer.scan.framework.time

import kotlin.math.round

/**
 * Round a number to a specified number of digits.
 */
private fun Float.roundTo(numberOfDigits: Int): Float {
    var multiplier = 1.0F
    repeat(numberOfDigits) { multiplier *= 10 }
    return round(this * multiplier) / multiplier
}

/**
 * Since kotlin time is still experimental, implement our own version for utility.
 */
sealed class Duration : Comparable<Duration> {

    companion object {
        val ZERO: Duration = DurationNanoseconds(0)
        val INFINITE: Duration = DurationInfinitePositive
        val NEGATIVE_INFINITE: Duration = DurationInfiniteNegative
    }

    abstract val inYears: Float

    abstract val inMonths: Float

    abstract val inWeeks: Float

    abstract val inDays: Float

    abstract val inHours: Float

    abstract val inMinutes: Float

    abstract val inSeconds: Float

    abstract val inMilliseconds: Float

    abstract val inMicroseconds: Float

    abstract val inNanoseconds: Long

    override fun compareTo(other: Duration): Int = inNanoseconds.compareTo(other.inNanoseconds)

    override fun equals(other: Any?): Boolean =
        if (other is Duration) inNanoseconds == other.inNanoseconds else false

    override fun hashCode(): Int = inNanoseconds.toInt()

    override fun toString(): String = when {
        inYears > 1 -> "${inYears.roundTo(2)} years"
        inMonths > 1 -> "${inMonths.roundTo(2)} months"
        inWeeks > 1 -> "${inWeeks.roundTo(2)} weeks"
        inDays > 1 -> "${inDays.roundTo(2)} days"
        inHours > 1 -> "${inHours.roundTo(2)} hours"
        inMinutes > 1 -> "${inMinutes.roundTo(2)} minutes"
        inSeconds > 1 -> "${inSeconds.roundTo(2)} seconds"
        inMilliseconds > 1 -> "${inMilliseconds.roundTo(2)} milliseconds"
        inMicroseconds > 1 -> "${inMicroseconds.roundTo(2)} microseconds"
        else -> "$inNanoseconds nanoseconds"
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
    override val inYears: Float = Float.POSITIVE_INFINITY
    override val inMonths: Float = Float.POSITIVE_INFINITY
    override val inWeeks: Float = Float.POSITIVE_INFINITY
    override val inDays: Float = Float.POSITIVE_INFINITY
    override val inHours: Float = Float.POSITIVE_INFINITY
    override val inMinutes: Float = Float.POSITIVE_INFINITY
    override val inSeconds: Float = Float.POSITIVE_INFINITY
    override val inMilliseconds: Float = Float.POSITIVE_INFINITY
    override val inMicroseconds: Float = Float.POSITIVE_INFINITY
    override val inNanoseconds: Long = Long.MAX_VALUE

    override fun toString(): String {
        return "INFINITE"
    }

    override operator fun unaryMinus(): Duration = DurationInfiniteNegative
}

private object DurationInfiniteNegative : DurationInfinite() {
    override val inYears: Float = Float.NEGATIVE_INFINITY
    override val inMonths: Float = Float.NEGATIVE_INFINITY
    override val inWeeks: Float = Float.NEGATIVE_INFINITY
    override val inDays: Float = Float.NEGATIVE_INFINITY
    override val inHours: Float = Float.NEGATIVE_INFINITY
    override val inMinutes: Float = Float.NEGATIVE_INFINITY
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
    override val inYears get(): Float = inDays / 365
    override val inMonths get(): Float = inDays / 30
    override val inWeeks get(): Float = inDays / 7
    override val inDays get(): Float = inHours / 24
    override val inHours get(): Float = inMinutes / 60
    override val inMinutes get(): Float = inSeconds / 60
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
