package com.example.shiftapp.dto.response

/**
 * Response DTO for `PATCH /api/shifts/bulk/submit`.
 *
 * Mirrors the `created` / `skipped` shape of [BulkCreateShiftResponse], but
 * here the unit is "an id we tried to flip" rather than "a calendar day":
 *  - [submitted] the shifts that successfully transitioned DRAFT → SUBMITTED
 *  - [skipped]   one entry per id that was rejected (with a typed reason)
 *
 * As with bulk-create, when the request used `atomic = true` and any id
 * would have been skipped the controller returns 409 instead of this body —
 * the caller never sees a half-applied result.
 */
data class BulkSubmitShiftResponse(
    val submitted: List<ShiftResponse>,
    val skipped: List<SkippedSubmit>,
)

/** A shift id that the bulk-submit flow chose not to flip, plus the reason. */
data class SkippedSubmit(
    val shiftId: Long,
    val reason: SkippedSubmitReason,
)

/**
 * Closed set of reasons a single id can be dropped from a bulk-submit.
 *
 * - [NOT_FOUND]                — the id doesn't exist (or was deleted)
 * - [NOT_OWNED_BY_REQUESTER]   — the shift exists but belongs to another user;
 *                                surfaced explicitly so the frontend can warn,
 *                                but rejected for security (IDOR prevention)
 * - [INVALID_STATUS_TRANSITION] — the shift is not currently DRAFT
 *                                (already SUBMITTED, APPROVED, REJECTED, …)
 */
enum class SkippedSubmitReason {
    NOT_FOUND,
    NOT_OWNED_BY_REQUESTER,
    INVALID_STATUS_TRANSITION,
}
