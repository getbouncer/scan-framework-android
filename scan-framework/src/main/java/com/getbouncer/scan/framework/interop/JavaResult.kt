package com.getbouncer.scan.framework.interop

import com.getbouncer.scan.framework.ResultHandler
import com.getbouncer.scan.framework.StateUpdatingResultHandler
import com.getbouncer.scan.framework.TerminatingResultHandler

/**
 * An implementation of a result handler that does not use suspending functions. This allows interoperability with java.
 */
abstract class JavaResultHandler<Input, State, Output> : ResultHandler<Input, State, Output> {
    override suspend fun onResult(result: Output, state: State, data: Input) = onResultJava(result, state, data)

    abstract fun onResultJava(result: Output, state: State, data: Input)
}

/**
 * An implementation of a state updating result handler that does not use suspending functions. This allows
 * interoperability with java.
 */
abstract class JavaStateUpdatingResultHandler<Input, State, Output> : StateUpdatingResultHandler<Input, State, Output> {
    override suspend fun onResult(result: Output, state: State, data: Input, updateState: (State) -> Unit) =
        onResultJava(result, state, data, updateState)

    abstract fun onResultJava(result: Output, state: State, data: Input, updateState: (State) -> Unit)
}

/**
 * An implementation of a terminating result handler that does not use suspending functions. This allows
 * interoperability with java.
 */
abstract class JavaTerminatingResultHandler<Input, State, Output> : TerminatingResultHandler<Input, State, Output> {
    override suspend fun onResult(result: Output, state: State, data: Input) = onResultJava(result, state, data)

    abstract fun onResultJava(result: Output, state: State, data: Input)
}
