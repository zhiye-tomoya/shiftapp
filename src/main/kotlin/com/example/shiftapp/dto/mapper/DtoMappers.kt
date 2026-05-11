package com.example.shiftapp.dto.mapper

import com.example.shiftapp.domain.Shift
import com.example.shiftapp.domain.ShiftRequest
import com.example.shiftapp.domain.ShiftTemplate
import com.example.shiftapp.dto.response.BulkCreateShiftResponse
import com.example.shiftapp.dto.response.BulkSubmitShiftResponse
import com.example.shiftapp.dto.response.ShiftRequestResponse
import com.example.shiftapp.dto.response.ShiftResponse
import com.example.shiftapp.dto.response.ShiftTemplateResponse
import com.example.shiftapp.service.BulkCreateOutcome
import com.example.shiftapp.service.BulkSubmitOutcome


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
        status = this.status.name,
        clockInTime = this.clockInTime,
        clockOutTime = this.clockOutTime,
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

/**
 * Map a service-level [BulkCreateOutcome] to its API DTO. We only need to
 * convert the `Shift` entities; `skipped` is already an API-shaped list
 * (see the layering note in [com.example.shiftapp.service.ShiftService.bulkCreate]).
 */
fun BulkCreateOutcome.toResponse(): BulkCreateShiftResponse =
    BulkCreateShiftResponse(
        created = this.created.map { it.toResponse() },
        skipped = this.skipped,
    )

fun BulkSubmitOutcome.toResponse(): BulkSubmitShiftResponse =
    BulkSubmitShiftResponse(
        submitted = this.submitted.map { it.toResponse() },
        skipped = this.skipped,
    )

/**
 * Map a [ShiftTemplate] entity to its API DTO.
 *
 * `shared` is derived from `ownerId == null` so the frontend doesn't have to
 * re-implement the same convention in two places (and we can't accidentally
 * drift the meaning of "shared" between layers).
 */
fun ShiftTemplate.toResponse(): ShiftTemplateResponse =
    ShiftTemplateResponse(
        id = this.id,
        name = this.name,
        clockInLocalTime = this.clockInLocalTime,
        clockOutLocalTime = this.clockOutLocalTime,
        // Defensive copy: `daysOfWeek` is a JPA-managed `@ElementCollection`
        // and we don't want callers (or Jackson) hanging onto a reference
        // into the persistence context.
        daysOfWeek = this.daysOfWeek.toSet(),
        roleTag = this.roleTag,
        ownerId = this.ownerId,
        shared = this.isGlobal(),
        version = this.version,
    )

