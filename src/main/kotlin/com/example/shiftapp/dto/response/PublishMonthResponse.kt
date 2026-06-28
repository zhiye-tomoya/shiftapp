package com.example.shiftapp.dto.response

/**
 * Response DTO for `POST /api/schedules/{yyyy-MM}/publish`.
 *
 * Three fields make the partial-success outcome explicit so the frontend
 * can render a confirmation screen ("12 shifts published, 1 skipped"):
 *  - [yearMonth]  the published month in ISO `YYYY-MM` form, echoed back so
 *                 the caller can correlate the response with the request
 *                 without re-parsing the URL.
 *  - [published]  the shifts that successfully transitioned APPROVED → PUBLISHED.
 *  - [skipped]    one entry per shift that was dropped, with a typed reason.
 *
 * Mirrors the `created` / `skipped` shape of [BulkCreateShiftResponse] and
 * [BulkSubmitShiftResponse] so the frontend gets a single mental model for
 * "transactional bulk lifecycle operations".
 *
 * When the request used `atomic = true` and any candidate would have been
 * skipped, the controller returns 409 Conflict instead of this body — i.e.
 * with `atomic = true` the client never sees a half-published month.
 */
data class PublishMonthResponse(
    val yearMonth: String,
    val published: List<ShiftResponse>,
    val skipped: List<SkippedPublish>,
)

/** A single shift the publish flow chose not to flip, plus the reason. */
data class SkippedPublish(
    val shiftId: Long,
    val reason: SkippedPublishReason,
)

/**
 * Closed set of reasons a single shift can be dropped from a publish.
 *
 * - [INVALID_STATUS_TRANSITION] — the shift is not currently APPROVED
 *   (still DRAFT/SUBMITTED, already REJECTED, or already PUBLISHED).
 *   The publish flow only fetches APPROVED shifts in the month window,
 *   so this reason mostly surfaces when a concurrent edit slipped it
 *   into a different state between the read and the lifecycle check.
 */
enum class SkippedPublishReason {
    INVALID_STATUS_TRANSITION,
}
