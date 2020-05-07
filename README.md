# CardScan Base

This repository contains the framework and machine learning models needed to quickly and accurately scan payment cards. [CardScan](https://cardscan.io/) is a relatively small library (1.9 MB) that provides fast and accurate payment card scanning.

Note this library does not contain any user interfaces. Another library, [CardScan UI](https://github.com/getbouncer/cardscan-ui-android) builds upon this one any adds simple user interfaces. 

CardScan serves as the foundation for CardVerify enterprise libraries, which validate the authenticity of payment cards as they are scanned.

![CardScan](docs/images/cardscan.png)

## Contents

* [Requirements](#requirements)
* [Demo](#demo)
* [Installation](#installation)
* [Using CardScan](#using-cardscan-base)
* [Developing CardScan](#developing-cardscan)
* [Authors](#authors)
* [License](#license)

## Requirements

* Android API level 21 or higher
* Kotlin coroutine compatibility

Note: Your app does not have to be written in kotlin to integrate cardscan, but must be able to depend on kotlin functionality.

## Demo

An app demonstrating the basic capabilities of CardScan is available in [github](https://github.com/getbouncer/cardscan-demo-android).

## Installation

The CardScan libraries are published in the [jcenter](https://jcenter.bintray.com/com/getbouncer/) repository, so for most gradle configurations you only need to add the dependencies to your app's `build.gradle` file:

```gradle
dependencies {
    implementation 'com.getbouncer:scan-framework:2.0.0004'
    implementation 'org.jetbrains.kotlinx:kotlinx-coroutines-core:1.3.3'
}
```

## Using cardscan-base

CardScan Base is designed to be used with [CardScan UI](https://github.com/getbouncer/cardscan-ui-android), which will provide user interfaces for scanning payment cards. However, it can be used independently.

For an overview of the architecture and design of the cardscan framework, see the [architecture documentation](docs/architecture.md).

### Processing unlimited data

Let's use an example where we process an unknown number of `MyData` values into `MyAnalyzerOutput` values, and then aggregate them into a single `MyAnalyzerOutput`.

First, create our input and output data types:
```kotlin
data class MyData(data: String)

data class MyAnalyzerOutput(output: Int)
```

Next, create an analyzer to process inputs into outputs, and a factory to create new instances of the analyzer.
```kotlin
class MyAnalyzer : Analyzer<MyData, Unit, MyAnalyzerOutput> {
    override suspend fun analyze(data: MyData, state: Unit): MyAnalyzerOutput = data.data.length
}

class MyAnalyzerFactory : AnalyzerFactory<MyAnalyzer> {
    override val isThreadSafe: Boolean = true
    
    override fun newInstance(): Analyzer? = MyAnalyzer()
}
```

Then, create a result handler to aggregate multiple outputs into one, and indicate when processing should cease.
```kotlin
class MyResultHandler(listener: ResultHanlder<MyData, Unit, MyAnalyzerOutput>) : StateUpdatingResultHandler<MyData, LoopState<Unit>, MyAnalyzerOutput>() {
    private var resultsReceived = 0
    private var totalResult = 0
    
    override suspend fun onResult(result: MyAnalyzerOutput, state: LoopState<Unit>, data: MyData, updateState: (LoopState<Unit>) -> Unit) {
        resultsReceived++
        if (resultsReceived > 10) {
            updateState(state.copy(finished = true))
            listener.onResult(totalResult, state, data)
        } else {
            totalResult += result.output
        }
    }
}
```

Finally, tie it all together with a class that receives data and does something with the result.
```kotlin
class MyDataProcessor : CoroutineScope, ResultHandler<MyData, Unit, MyAnalyzerOutput> {

    private val analyzerPool = AnalyzerPool.Factory(MyAnalyzerFactory(), 4)
    private val resultHandler = MyResultHandler(this)
    private val loop: AnalyzerLoop<MyData, MyAnalyzerOutput> by lazy {
        ProcessBoundAnalyzerLoop(analyzerPool, resultHandler, Unit, "my_loop")
    }
    
    fun start() {
        loop.start()
    }
    
    fun onReceiveData(data: MyData) {
        loop.processFrame(data)
    }
    
    fun onResult(result: MyAnalyzerOutput, state: Unit, data: MyData) {
        // Display something
    }
}
```

### Processing a known amount of data

In this example, we need to process a known amount of data as quickly as possible using multiple analyzers.

First, create our input and output data types:
```kotlin
data class MyData(data: String)

data class MyAnalyzerOutput(output: Int)
```

Next, create an analyzer to process inputs into outputs, and a factory to create new instances of the analyzer.
```kotlin
class MyAnalyzer : Analyzer<MyData, Unit, MyAnalyzerOutput> {
    override suspend fun analyze(data: MyData, state: Unit): MyAnalyzerOutput = data.data.length
}

class MyAnalyzerFactory : AnalyzerFactory<MyAnalyzer> {
    override val isThreadSafe: Boolean = true
    
    override fun newInstance(): Analyzer? = MyAnalyzer()
}
```

Finally, tie it all together with a class that processes the data and does something with the results.
```kotlin
class MyDataProcessor(dataToProcess: List<MyData>) : CoroutineScope, TerminatingResultHandler<MyData, Unit, MyAnalyzerOutput> {

    override val coroutineContext: CoroutineContext = Dispatchers.Default

    private val analyzerFactory = MyAnalyzerFactory()
    private val resultHandler = MyResultHandler(this)
    private val analyzerPool = AnalyzerPool(analyzerFactory)

    private val loop: AnalyzerLoop<MyData, Unit, MyAnalyzerOutput> by lazy {
        FiniteAnalyzerLoop(
            frames = dataToProcess,
            analyzerPool = analyzerPool,
            resultHandler = this,
            initialState = Unit,
            events = this.events(),
            name = "loop_name",
            onAnalyzerFailure = {
                runOnUiThread { analyzerFailure(it) }
                true // terminate the loop on any analyzer failures
            },
            timeLimit = 10.seconds
        )
    }
    
    fun start() {
        loop.start()
    }
    
    override fun onResult(result: MyAnalyzerOutput, state: Unit, data: MyData) {
        // A single frame has been processed
    }

    override fun onAllDataProcessed() {
        // Notify that all data has been processed
    }

    override fun onTerminatedEarly() {
        // Notify that not all data was processed
    }

    private fun analyzerFailure(cause: Throwable?) {
        // Notify that the data processing failed
    }
}
```

### Processing images from a camera

Let's look at an example where we process images from a camera in the format of `PreviewImage` until a `PaymentCardImageResultAggregator` determines a final `OcrPaymentCard` result.

```kotlin
class MyCameraAnalyzer : CoroutineScope, AggregateResultListener<PreviewImage, Unit, OcrPaymentCard, PaymentCard> {

    override val coroutineContext: CoroutineContext = Dispatchers.Default

    private val analyzerLoader = SSDOcr.ModelLoader(this)
    private val analyzerFactory = SSDOcr.Factory(this, analyzerLoader)
    private val analyzerPool = AnalyzerPool(analyzerFactory)

    private val resultHandler = PaymentCardImageResultAggregator(
        config = ResultAggregatorConfig.Builder().build(),
        events = this.events(),
        listener = this
    )

    private val loop: AnalyzerLoop<PreviewImage, Unit, OcrPaymentCard> by lazy {
        ProcessBoundAnalyzerLoop(
            analyzerPool = analyzerPool,
            resultHandler = resultHandler,
            initialState = Unit,
            events = this.events(),
            name = "analyzer_loop",
            onAnalyzerFailure = {
                runOnUiThread { analyzerFailure(it) }
                true // terminate the loop on any analyzer failures
            }
        )
    }
    
    fun startAnalyzing() {
        loop.start()
    }

    fun onCameraFrame(frame: PreviewImage) {
        loop.processFrame(frame)
    }
    
    /*
     * The following methods are part of the [AggregateResultListener]. 
     */
    override fun onResult(result: PaymentCard, frames: Map<String, List<SavedFrame<PreviewImage, Unit, OcrPaymentCard>>>) {
        // do something with the final result.
    }
    
    override fun onInterimResult(result: OcrPaymentCard, state: Unit, frame: PreviewImage, isFirstValidResult: Boolean) {
        // do something with an interim result.
    }
    
    override fun onInvalidResult(result: OcrPaymentCard, state: Unit, frame: PreviewImage, hasPreviousValidResult: Boolean) {
        // do something with an invalid result.
    }
    
    override fun onUpdateProcessingRate(overallRate: Rate, instantRate: Rate) {
        // do something with the processing rate.
    }

    private fun analyzerFailure(cause: Throwable?) {
        // Notify that the data processing failed
    }
}
```

## Developing CardScan

See the [development documentation](docs/develop.md) for details on developing for CardScan.

## Authors

Adam Wushensky, Sam King, and Zain ul Abi Din

## License

CardScan is available under paid and free licenses. See the [LICENSE](LICENSE) file for the full license text.

### Quick summary

In short, CardScan will remain free forever for non-commercial applications, but use by commercial applications is limited to 90 days, after which time a licensing agreement is required. We're also adding some legal liability protections.

After this period commercial applications need to convert to a licensing agreement to continue to use CardScan.
* Details of licensing (pricing, etc) are available at [https://cardscan.io/pricing](https://cardscan.io/pricing), or you can contact us at [license@getbouncer.com](mailto:license@getbouncer.com).

### More detailed summary

What's allowed under the license:
* Free use for any app for 90 days (for demos, evaluations, hackathons, etc).
* Contributions (contributors must agree to the [Contributor License Agreement](Contributor%20License%20Agreement))
* Any modifications as needed to work in your app

What's not allowed under the license:
* Commercial applications using the license for longer than 90 days without a license agreement. 
* Using us now in a commercial app today? No worries! Just email [license@getbouncer.com](mailto:license@getbouncer.com) and we’ll get you set up.
* Redistribution under a different license
* Removing attribution
* Modifying logos
* Indemnification: using this free software is ‘at your own risk’, so you can’t sue Bouncer Technologies, Inc. for problems caused by this library

Questions? Concerns? Please email us at [license@getbouncer.com](mailto:license@getbouncer.com) or ask us on [slack](https://getbouncer.slack.com).
