package com.getbouncer.scan.framework.ml

import android.util.Log
import com.getbouncer.scan.framework.Analyzer
import com.getbouncer.scan.framework.AnalyzerFactory
import com.getbouncer.scan.framework.Config
import com.getbouncer.scan.framework.Loader
import com.getbouncer.scan.framework.time.Timer
import java.nio.ByteBuffer
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.tensorflow.lite.Interpreter

/**
 * A TensorFlowLite analyzer uses an [Interpreter] to analyze data.
 */
abstract class TensorFlowLiteAnalyzer<Input, MLInput, Output, MLOutput>(
    private val tfInterpreter: Interpreter,
    private val debug: Boolean = false
) : Analyzer<Input, Unit, Output> {

    protected abstract fun buildEmptyMLOutput(): MLOutput

    protected abstract fun interpretMLOutput(data: Input, mlOutput: MLOutput): Output

    protected abstract fun transformData(data: Input): MLInput

    protected abstract fun executeInference(tfInterpreter: Interpreter, data: MLInput, mlOutput: MLOutput)

    private val loggingTimer by lazy {
        Timer.newInstance(Config.logTag, "$name ${this::class.java.simpleName}", enabled = debug)
    }

    override suspend fun analyze(data: Input, state: Unit): Output {
        val mlInput = loggingTimer.measure("transform") {
            transformData(data)
        }

        val mlOutput = loggingTimer.measure("prepare") {
            buildEmptyMLOutput()
        }

        loggingTimer.measure("infer") {
            executeInference(tfInterpreter, mlInput, mlOutput)
        }

        return loggingTimer.measure("interpret") {
            interpretMLOutput(data, mlOutput)
        }
    }

    fun close() = tfInterpreter.close()
}

/**
 * A factory that creates tensorflow models as analyzers.
 */
abstract class TFLAnalyzerFactory<Output : Analyzer<*, *, *>>(private val loader: Loader) : AnalyzerFactory<Output> {
    protected abstract val tfOptions: Interpreter.Options

    private val loadModelMutex = Mutex()

    private var loadedModel: ByteBuffer? = null

    protected suspend fun createInterpreter(): Interpreter? {
        val modelData = loadModel()
        return if (modelData == null) {
            Log.e(Config.logTag, "Unable to load model")
            null
        } else {
            Interpreter(modelData, tfOptions)
        }
    }

    private suspend fun loadModel(): ByteBuffer? = loadModelMutex.withLock {
        var loadedModel = this.loadedModel
        if (loadedModel == null) {
            loadedModel = loader.loadData()
            this.loadedModel = loadedModel
        }
        loadedModel
    }
}
