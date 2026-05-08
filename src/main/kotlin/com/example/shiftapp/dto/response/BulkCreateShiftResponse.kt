package com.example.shiftapp.dto.response

import java.time.LocalDate

/**
 * Response DTO for `POST /api/shifts/bulk`.
 *
 * Two parallel arrays make the partial-success outcome explicit:
 *  - [created]  the shifts that actually landed in the DB (all DRAFT)
 *  - [skipped]  one entry per candidate day that was rejected, with a coded reason
 *
 * When the request used `atomic = true` and any candidate would have been
 * skipped, the controller returns 409 Conflict instead of this body — i.e.
 * with `atomic = true` the client never sees a half-applied response.
 */
data class BulkCreateShiftResponse(
    val created: List<ShiftResponse>,
    val skipped: List<SkippedShift>,
)

/**
 * One day that the bulk-create flow chose not to persist, plus the machine-
 * readable reason. The frontend uses [reason] to localise / colour the diff.
 */
data class SkippedShift(
    val date: LocalDate,
    val reason: SkippedShiftReason,
)

/**
 * Closed set of reasons a single day can be dropped from a bulk-create.
 *
 * Kept as a top-level enum (not a `String`) so adding a new reason is a
 * compile-time event everywhere — frontend types stay in sync via codegen.
 */
enum class SkippedShiftReason {
    /** A shift already exists for this user that overlaps the requested window. */
    OVERLAPPING_EXISTING_SHIFT,
}
