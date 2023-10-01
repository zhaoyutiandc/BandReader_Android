package com.example.bandReader.data

import kotlinx.serialization.Serializable

@Serializable
data class AppConfig(var showLog: Boolean = false,var boost: Boolean = false)