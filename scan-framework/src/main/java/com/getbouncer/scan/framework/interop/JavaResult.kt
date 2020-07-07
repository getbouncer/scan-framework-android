package com.getbouncer.scan.framework.interop

import com.getbouncer.scan.framework.ResultHandler
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
