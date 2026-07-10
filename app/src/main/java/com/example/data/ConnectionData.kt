package com.example.data

import kotlinx.serialization.Serializable

@Serializable
data class ConnectionData(
    val version: Int = 2,
    val ip: String,
    val port: Int,
    val mode: String,
    val checksum: String? = null
)

enum class TransferScope {
    DATA_ONLY,
    IMAGES_ONLY,
    FULL_ARCHIVE
}
