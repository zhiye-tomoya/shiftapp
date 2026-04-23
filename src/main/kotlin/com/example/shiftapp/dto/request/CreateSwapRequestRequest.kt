package com.example.shiftapp.dto.request

import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Positive

/**
 * Request DTO for creating a new shift swap request.
 *
 * A staff member uses this to request swapping their shift with another user.
 * The shift must be APPROVED before a swap can be requested.
 */
data class CreateSwapRequestRequest(
    @field:NotNull(message = "Shift ID is required")
    @field:Positive(message = "Shift ID must be positive")
    val shiftId: Long,

    @field:NotNull(message = "Target user ID is required")
    @field:Positive(message = "Target user ID must be positive")
    val targetUserId: Long
)
