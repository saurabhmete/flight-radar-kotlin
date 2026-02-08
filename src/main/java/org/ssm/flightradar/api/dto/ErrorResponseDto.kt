package org.ssm.flightradar.api.dto

import kotlinx.serialization.Serializable

@Serializable
data class ErrorResponseDto(
    val error: String,
    val details: String? = null
)
