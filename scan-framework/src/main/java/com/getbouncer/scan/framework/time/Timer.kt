package com.getbouncer.scan.framework.time

import android.util.Log
import com.getbouncer.scan.framework.Config

sealed class Timer {

    companion object {
        fun newInstance(
            tag: String,
            name: String,
            updateInterval: Duration = 2.seconds,
            enabled: Boolean = Config.isDebug
        ) = if (enabled) {
            LoggingTimer(
                tag,
                name,
                updateInterval
            )
        } else {
            NoOpTimer
        }
    }

    abstract fun <T> measure(taskName: String? = null, task: () -> T): T
}

private object NoOpTimer : Timer() {
    override fun <T> measure(taskName: String?, task: () -> T): T = task()
}

private class LoggingTimer(
    private val tag: String,
    private val name: String,
    private val updateInterval: Duration
) : Timer() {
    private var executionCount = 0
    private var executionTotalDuration = Duration.ZERO
    private var updateClock = Clock.markNow()

    override fun <T> measure(taskName: String?, task: () -> T): T {
        val (duration, result) = measureTimeWithResult { task() }

        executionCount++
        executionTotalDuration += duration

        if (updateClock.elapsedSince() > updateInterval) {
            updateClock = Clock.markNow()
            Log.d(tag,
                "$name${if (!taskName.isNullOrEmpty()) ".$taskName" else ""} executing on " +
                        "thread ${Thread.currentThread().name} " +
                        "AT ${executionCount / executionTotalDuration.inSeconds} FPS, " +
                        "${executionTotalDuration.inMilliseconds / executionCount} MS/F"

            )
        }
        return result
    }
}
