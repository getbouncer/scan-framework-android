package com.getbouncer.scan.framework.ml.ssd

import com.getbouncer.scan.framework.util.updateEach
import kotlin.math.exp

typealias ClassifierScores = FloatArray

/**
 * Compute softmax for each row. This will replace each row value with a value normalized by the
 * sum of all the values in the same row.
 */
fun ClassifierScores.softMax2D() {
    val rowSumExp = this.fold(0F) { acc, element -> acc + exp(element) }
    this.updateEach { exp(it) / rowSumExp }
}
