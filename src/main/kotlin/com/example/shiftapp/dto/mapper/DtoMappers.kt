package com.example.shiftapp.dto.mapper

import com.example.shiftapp.domain.Shift
import com.example.shiftapp.domain.ShiftRequest
import com.example.shiftapp.dto.response.ShiftResponse
import com.example.shiftapp.dto.response.ShiftRequestResponse

/**
 * Extension functions to map domain models to DTOs.
 *
 * These mappers keep the conversion logic in one place.
 * Usage: shift.toResponse() instead of ShiftResponse(shift.id, ...)
 *
 * Why extension functions?
 * - Clean syntax: shift.toResponse() reads naturally
 * - No utility classes needed
 * - Easy to find and maintain all mapping logic
 */

fun Shift.toResponse(): ShiftResponse {
    return ShiftResponse(
        id = this.id,
        userId = this.userId,
        status = this.status.name
    )
}

fun ShiftRequest.toResponse(): ShiftRequestResponse {
    return ShiftRequestResponse(
        id = this.id,
        shift = this.shift.toResponse(),
        requesterId = this.requesterId,
        targetUserId = this.targetUserId,
        status = this.status.name
    )
}
