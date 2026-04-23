package com.example.shiftapp.dto.response

/**
 * Response DTO for Shift entity.
 *
 * This is what we send back to clients when they query shifts.
 * Notice: We convert the status enum to a String for the API.
 */
data class ShiftResponse(
    val id: Long,
    val userId: Long,
    val status: String
)
