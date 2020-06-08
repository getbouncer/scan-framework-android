package com.getbouncer.scan.framework.interop

import com.getbouncer.scan.framework.Analyzer
import com.getbouncer.scan.framework.AnalyzerFactory
import com.getbouncer.scan.framework.AnalyzerPool
import com.getbouncer.scan.framework.DEFAULT_ANALYZER_PARALLEL_COUNT

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

/**
 * An implementation of an analyzer pool factory that does not use suspending functions. This allows interoperability
 * with java.
 */
class JavaAnalyzerPoolFactory<DataFrame, State, Output> @JvmOverloads constructor(
    private val analyzerFactory: JavaAnalyzerFactory<out Analyzer<DataFrame, State, Output>>,
    private val desiredAnalyzerCount: Int = DEFAULT_ANALYZER_PARALLEL_COUNT
) {
    fun buildAnalyzerPool() = AnalyzerPool(
        desiredAnalyzerCount = desiredAnalyzerCount,
        analyzers = (0 until desiredAnalyzerCount).mapNotNull { analyzerFactory.newInstanceJava() }
    )
}
