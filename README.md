# Scan Framework

This repository contains the framework needed to quickly and accurately scan items (payment cards, IDs, etc.). [CardScan](https://cardscan.io/) is a relatively small library (1.9 MB) that provides fast and accurate payment card scanning.

Note this library does not contain any user interfaces or ML models. Other libraries [Scan Payment](https://github.com/getbouncer/scan-payment-android) and [CardScan UI](https://github.com/getbouncer/cardscan-ui-android) build upon this and add ML models and simple user interfaces. 

Scan serves as the foundation for CardScan and CardVerify enterprise libraries, which validate the authenticity of payment cards as they are scanned.

![CardScan](docs/images/cardscan.png)

## Contents

* [Requirements](#requirements)
* [Demo](#demo)
* [Installation](#installation)
* [Using Scan Framework](#using-scan-framework)
* [Developing Scan Framework](#developing-scan-framework)
* [Authors](#authors)
* [License](#license)

## Requirements

* Android API level 21 or higher
* Kotlin coroutine compatibility

Note: Your app does not have to be written in kotlin to integrate scan-framework, but must be able to depend on kotlin functionality.

## Demo

An app demonstrating the basic capabilities of scan-framework is available in [github](https://github.com/getbouncer/cardscan-demo-android).

## Installation

The scan-framework libraries are published in the [jcenter](https://jcenter.bintray.com/com/getbouncer/) repository, so for most gradle configurations you only need to add the dependencies to your app's `build.gradle` file:

```gradle
dependencies {
    implementation 'com.getbouncer:scan-framework:2.0.0008'
}
```

## Using scan-framework

scan-framework is designed to be used with [scan-payment](https://github.com/getbouncer/scan-payment-android), which will provide user interfaces for scanning payment cards. However, it can be used independently.

For an overview of the architecture and design of the scan-framework, see the [architecture documentation](docs/architecture.md).

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

## Developing scan-framework

See the [development documentation](docs/develop.md) for details on developing for scan-framework.

## Authors

Adam Wushensky, Sam King, and Zain ul Abi Din

## License

scan-framework is available under paid and free licenses. See the [LICENSE](LICENSE) file for the full license text.

### Quick summary

In short, scan-framework will remain free forever for non-commercial applications, but use by commercial applications is limited to 90 days, after which time a licensing agreement is required. We're also adding some legal liability protections.

After this period commercial applications need to convert to a licensing agreement to continue to use scan-framework.
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
