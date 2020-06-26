package com.getbouncer.scan.framework.api.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ModelUpgradeResponse(
    @SerialName("model_url") val modelUrl: String,
    @SerialName("model_version") val modelVersion: String,
    @SerialName("hash") val hash: String,
    @SerialName("hash_algorithm") val hashAlgorithm: String
)
