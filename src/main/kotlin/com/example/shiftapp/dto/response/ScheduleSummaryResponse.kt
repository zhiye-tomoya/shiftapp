package com.example.shiftapp.dto.response

/**
 * Response DTO for `GET /api/schedules/{yyyy-MM}`.
 *
 * A small status histogram for one calendar month — designed to drive the
 * publish-confirmation UI ("you're about to publish 12 APPROVED shifts").
 * Kept intentionally cheap: just counts, no per-shift detail, so the ADMIN
 * dashboard can render it on every page load without pulling thousands of
 * rows. For the full list of shifts in a month, callers should use
 * `GET /api/shifts?from&to`.
 *
 * The window is `[yearMonth.atDay(1), yearMonth.plusMonths(1).atDay(1))`
 * keyed on `clockInTime`, consistent with the publish flow.
 */
data class ScheduleSummaryResponse(
    val yearMonth: String,
    val draft: Long,
    val submitted: Long,
    val approved: Long,
    val rejected: Long,
    val published: Long,
    val total: Long,
)
