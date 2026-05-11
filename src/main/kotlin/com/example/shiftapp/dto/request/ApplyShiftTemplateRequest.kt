package com.example.shiftapp.dto.request

import jakarta.validation.constraints.AssertTrue
import jakarta.validation.constraints.NotNull
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/**
 * Request DTO for `POST /api/shifts/bulk/from-template`.
 *
 * Anchors a stored [com.example.shiftapp.domain.ShiftTemplate] to a calendar
 * range. The owning user is **always** the caller (taken from the JWT), same
 * IDOR-defence rule as [BulkCreateShiftRequest] — STAFF cannot apply a
 * template "on behalf of" anyone else. (An ADMIN-driven "apply for another
 * user" flow is a future task, intentionally separate so the permission
 * surface is small.)
 *
 * Validation mirrors [BulkCreateShiftRequest] so the API is consistent:
 *  - `endDate` must be on or after `startDate`
 *  - range capped at 92 days as a runaway-request safety net
 *  - the template's time window / daysOfWeek pattern is enforced by the
 *    template itself, so we don't repeat those checks here
 */
data class ApplyShiftTemplateRequest(
    @field:NotNull(message = "templateId is required")
    val templateId: Long,

    @field:NotNull(message = "startDate is required")
    val startDate: LocalDate,

    @field:NotNull(message = "endDate is required")
    val endDate: LocalDate,

    /** When `true`, days overlapping an existing shift are skipped instead of erroring. */
    val skipOverlapping: Boolean = true,

    /** When `true`, ANY skip aborts the whole batch and rolls back. */
    val atomic: Boolean = false,
) {
    @AssertTrue(message = "endDate must be on or after startDate")
    fun isDateRangeValid(): Boolean = !endDate.isBefore(startDate)

    @AssertTrue(message = "Range must be at most 92 days")
    fun isRangeWithinLimit(): Boolean =
        ChronoUnit.DAYS.between(startDate, endDate) <= 92
}
