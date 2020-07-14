package com.getbouncer.scan.framework.interop

import com.getbouncer.scan.framework.AggregateResultListener
import com.getbouncer.scan.framework.ResultAggregator
import com.getbouncer.scan.framework.ResultAggregatorConfig
import com.getbouncer.scan.framework.ResultHandler
import com.getbouncer.scan.framework.SavedFrame
import com.getbouncer.scan.framework.StatefulResultHandler
import com.getbouncer.scan.framework.TerminatingResultHandler

/**
 * An implementation of a result handler that does not use suspending functions. This allows interoperability with java.
 */
abstract class JavaResultHandler<Input, Output, Verdict> : ResultHandler<Input, Output, Verdict> {
    override suspend fun onResult(result: Output, data: Input) = onResultJava(result, data)

    abstract fun onResultJava(result: Output, data: Input): Verdict
}

/**
 * An implementation of a stateful result handler that does not use suspending functions. This allows interoperability
 * with java.
 */
abstract class JavaStatefulResultHandler<Input, State, Output, Verdict>(
    initialState: State
) : StatefulResultHandler<Input, State, Output, Verdict>(initialState) {
    override suspend fun onResult(result: Output, data: Input): Verdict = onResultJava(result, data)

    abstract fun onResultJava(result: Output, data: Input): Verdict
}

/**
 * An implementation of a terminating result handler that does not use suspending functions. This allows
 * interoperability with java.
 */
abstract class JavaTerminatingResultHandler<Input, State, Output>(
    initialState: State
) : TerminatingResultHandler<Input, State, Output>(initialState) {
    override suspend fun onResult(result: Output, data: Input) = onResultJava(result, data)

    abstract fun onResultJava(result: Output, data: Input)
}

/**
 * An implementation of a result listener that does not use suspending functions. This allows interoperability with
 * java.
 */
abstract class JavaAggregateResultListener<DataFrame, State, InterimResult, FinalResult>
    : AggregateResultListener<DataFrame, State, InterimResult, FinalResult> {
    override suspend fun onInterimResult(result: InterimResult, state: State, frame: DataFrame) =
        onJavaInterimResult(result, state, frame)

    override suspend fun onResult(
        result: FinalResult,
        frames: Map<String, List<SavedFrame<DataFrame, State, InterimResult>>>
    ) = onJavaResult(result, frames)

    override suspend fun onReset() = onJavaReset()

    abstract fun onJavaInterimResult(result: InterimResult, state: State, frame: DataFrame)

    abstract fun onJavaResult(
        result: FinalResult,
        frames: Map<String, List<SavedFrame<DataFrame, State, InterimResult>>>
    )

    abstract fun onJavaReset()
}

/**
 * An implementation of a result aggregator that does not use suspending functions. This allows interoperability with
 * java.
 */
abstract class JavaResultAggregator<DataFrame, State, AnalyzerResult, InterimResult, FinalResult>(
    config: ResultAggregatorConfig,
    listener: AggregateResultListener<DataFrame, State, InterimResult, FinalResult>,
    initialState: State
) : ResultAggregator<DataFrame, State, AnalyzerResult, InterimResult, FinalResult>(config, listener, initialState) {
    override suspend fun aggregateResult(
        result: AnalyzerResult,
        startAggregationTimer: () -> Unit,
        mustReturnFinal: Boolean
    ): Pair<InterimResult, FinalResult?> = javaAggregateResult(result, startAggregationTimer, mustReturnFinal)

    abstract fun javaAggregateResult(
        result: AnalyzerResult,
        startAggregationTimer: () -> Unit,
        mustReturnFinal: Boolean
    ): Pair<InterimResult, FinalResult?>
}
