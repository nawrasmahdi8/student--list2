package com.example.data

import kotlinx.serialization.Serializable

@Serializable
data class ConnectionData(
    val ipAddress: String,
    val port: Int,
    val scope: TransferScope
)

enum class TransferScope {
    DATA_ONLY,
    IMAGES_ONLY,
    FULL_ARCHIVE
}
