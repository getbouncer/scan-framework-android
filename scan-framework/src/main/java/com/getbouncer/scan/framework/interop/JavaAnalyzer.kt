package com.getbouncer.scan.framework.interop

import com.getbouncer.scan.framework.Analyzer
import com.getbouncer.scan.framework.AnalyzerFactory

/**
 * An implementation of an analyzer that does not use suspending functions. This allows interoperability with java.
 */
abstract class JavaAnalyzer<Input, State, Output> : Analyzer<Input, State, Output> {
    override suspend fun analyze(data: Input, state: State): Output = analyzeJava(data, state)

    abstract fun analyzeJava(data: Input, state: State): Output
}

/**
 * An implementation of an analyzer factory that does not use suspending functions. This allows interoperability with
 * java.
 */
abstract class JavaAnalyzerFactory<Output : Analyzer<*, *, *>> : AnalyzerFactory<Output> {
    override suspend fun newInstance(): Output? = newInstanceJava()

    abstract fun newInstanceJava(): Output?
}
