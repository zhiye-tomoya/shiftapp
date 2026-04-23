package com.example.shiftapp.dto.request

import jakarta.validation.constraints.NotNull

/**
 * Request DTO for creating a new shift.
 *
 * This is what a client sends when they want to create a new shift in DRAFT status.
 */
data class CreateShiftRequest(
    @field:NotNull(message = "User ID is required")
    val userId: Long
)
