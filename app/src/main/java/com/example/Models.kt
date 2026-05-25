package com.example

import android.net.Uri

data class DeviceInfo(
    val name: String,
    val ip: String,
    val httpPort: Int,
    val udpPort: Int
)

data class SelectedFile(
    val uri: Uri,
    val name: String,
    val size: Long
)

data class ReceivedFile(
    val name: String,
    val size: Long,
    val timestamp: Long,
    val path: String
)
