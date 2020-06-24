package com.getbouncer.scan.framework.api.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ModelUpgradeResponse(
    @SerialName("model_url") val modelUrl: String,
    @SerialName("sha256") val sha256: String
)
