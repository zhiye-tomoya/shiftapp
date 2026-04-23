package com.example.shiftapp.dto.response

/**
 * Response DTO for ShiftRequest entity.
 *
 * Includes the full shift information nested inside.
 * Clients can see all details about the swap request.
 */
data class ShiftRequestResponse(
    val id: Long,
    val shift: ShiftResponse,
    val requesterId: Long,
    val targetUserId: Long,
    val status: String
)
