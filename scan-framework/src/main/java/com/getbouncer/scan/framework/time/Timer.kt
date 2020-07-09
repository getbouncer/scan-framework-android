package com.getbouncer.scan.framework.time

import android.util.Log
import com.getbouncer.scan.framework.Config
import kotlinx.coroutines.runBlocking

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

    fun <T> measure(taskName: String? = null, task: () -> T): T = runBlocking {
        measureSuspend { task() }
    }

    abstract suspend fun <T> measureSuspend(taskName: String? = null, task: suspend () -> T): T
}

private object NoOpTimer : Timer() {
    override suspend fun <T> measureSuspend(taskName: String?, task: suspend () -> T): T = task()
}

private class LoggingTimer(
    private val tag: String,
    private val name: String,
    private val updateInterval: Duration
) : Timer() {
    private var executionCount = 0
    private var executionTotalDuration = Duration.ZERO
    private var updateClock = Clock.markNow()

    override suspend fun <T> measureSuspend(taskName: String?, task: suspend () -> T): T {
        val (duration, result) = measureTimeWithResult { task() }

        executionCount++
        executionTotalDuration += duration

        if (updateClock.elapsedSince() > updateInterval) {
            updateClock = Clock.markNow()
            Log.d(
                tag,
                "$name${if (!taskName.isNullOrEmpty()) ".$taskName" else ""} executing on " +
                    "thread ${Thread.currentThread().name} " +
                    "AT ${executionCount / executionTotalDuration.inSeconds} FPS, " +
                    "${executionTotalDuration.inMilliseconds / executionCount} MS/F"
            )
        }
        return result
    }
}
