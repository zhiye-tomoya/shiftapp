package com.example.shiftapp.dto.response

import java.time.LocalDateTime

/**
 * Standardized error response for API errors.
 *
 * When something goes wrong, we always return this format so clients
 * can handle errors consistently. This makes API integration much easier!
 */
data class ErrorResponse(
    val timestamp: LocalDateTime = LocalDateTime.now(),
    val status: Int,
    val error: String,
    val message: String,
    val path: String
)
