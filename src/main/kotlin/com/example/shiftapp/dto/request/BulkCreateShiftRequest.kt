package com.example.shiftapp.dto.request

import jakarta.validation.constraints.AssertTrue
import jakarta.validation.constraints.NotNull
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.temporal.ChronoUnit

/**
 * Request DTO for `POST /api/shifts/bulk`.
 *
 * Lets a STAFF user lay down many DRAFT shifts in one round-trip — typically
 * "the same daily window across a date range" (e.g. Mon–Fri 09:00–18:00 next week).
 *
 * The owning [userId] is intentionally NOT part of this DTO: the server fills it
 * in from the JWT principal so a caller can never bulk-create shifts for someone
 * else. The endpoint also commits to a single status (DRAFT) — promotion to
 * SUBMITTED happens via the dedicated `PATCH /api/shifts/bulk/submit` endpoint.
 *
 * Validation:
 *  - `endDate` must be on or after `startDate` (single-day ranges are allowed)
 *  - the local-time window must be strictly positive (`clockOutLocalTime` >
 *    `clockInLocalTime`); we don't currently support overnight windows here
 *    because they'd straddle two calendar days
 *  - `daysOfWeek == null` means "every day in the range"; an empty set is
 *    rejected because that's almost always a bug at the call site
 *  - the range is capped at 92 days as a cheap safety net against runaway
 *    requests; we can lift this later if/when we add background processing
 */
data class BulkCreateShiftRequest(
    @field:NotNull(message = "startDate is required")
    val startDate: LocalDate,

    @field:NotNull(message = "endDate is required")
    val endDate: LocalDate,

    /** `null` means "every day in the range". An empty set is rejected. */
    val daysOfWeek: Set<DayOfWeek>? = null,

    @field:NotNull(message = "clockInLocalTime is required")
    val clockInLocalTime: LocalTime,

    @field:NotNull(message = "clockOutLocalTime is required")
    val clockOutLocalTime: LocalTime,

    /** When `true`, days that overlap an existing shift are skipped instead of erroring. */
    val skipOverlapping: Boolean = true,

    /** When `true`, ANY skip (overlap, etc.) aborts the whole batch and rolls back. */
    val atomic: Boolean = false,
) {
    @AssertTrue(message = "endDate must be on or after startDate")
    fun isDateRangeValid(): Boolean = !endDate.isBefore(startDate)

    @AssertTrue(message = "clockOutLocalTime must be after clockInLocalTime")
    fun isTimeRangeValid(): Boolean = clockOutLocalTime.isAfter(clockInLocalTime)

    @AssertTrue(message = "Range must be at most 92 days")
    fun isRangeWithinLimit(): Boolean =
        ChronoUnit.DAYS.between(startDate, endDate) <= 92

    @AssertTrue(message = "daysOfWeek must be null or non-empty")
    fun isDaysOfWeekShapeValid(): Boolean =
        daysOfWeek == null || daysOfWeek.isNotEmpty()
}
