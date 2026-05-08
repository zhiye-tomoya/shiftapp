package com.example.shiftapp.dto.request

import jakarta.validation.constraints.AssertTrue
import java.time.LocalDateTime

/**
 * Request DTO for `PATCH /api/shifts/{id}` — partial update.
 *
 * Every field is optional; the server merges the supplied fields onto the
 * persisted shift and validates invariants on the merged result.
 *
 * For full-replacement semantics use `PUT /api/shifts/{id}` ([ReplaceShiftRequest]).
 *
 * Permission matrix (enforced server-side, see [com.example.shiftapp.service.ShiftService.updateShift]):
 *
 *  | Role  | DRAFT | SUBMITTED | APPROVED | REJECTED |
 *  |-------|:-----:|:---------:|:--------:|:--------:|
 *  | STAFF |  own  |    ❌     |    ❌    |    ❌    |
 *  | ADMIN |  any  |    any    |   any    |   any    |
 *
 *  Reassigning [userId] is **ADMIN-only** regardless of status.
 *
 * Optimistic concurrency: [version] is optional but recommended; when present
 * it must equal the persisted row's version or the request is rejected with
 * 409 *before* any write happens. Even when omitted, JPA's `@Version` check
 * fires at flush as a backstop.
 *
 * Cross-field validation: when only one of `clockInTime` / `clockOutTime` is
 * supplied, we can't validate the range here (the other half lives on the
 * persisted entity); the service does the merged-result check after lookup.
 * When *both* are supplied we fail fast at the boundary via
 * [isPartialTimeRangeValid].
 */
data class UpdateShiftRequest(
    val clockInTime: LocalDateTime? = null,
    val clockOutTime: LocalDateTime? = null,

    /** ADMIN-only owner reassignment. STAFF callers must omit (or echo) this. */
    val userId: Long? = null,

    /** Optimistic-lock version snapshot the client last read. Optional. */
    val version: Long? = null,
) {
    @AssertTrue(message = "clockOutTime must be after clockInTime")
    fun isPartialTimeRangeValid(): Boolean {
        if (clockInTime == null || clockOutTime == null) return true
        return clockOutTime.isAfter(clockInTime)
    }

    /**
     * A PATCH with no fields is almost always a client bug — reject it at
     * the boundary instead of silently no-op'ing in the service.
     */
    @AssertTrue(message = "At least one field (clockInTime, clockOutTime, userId) must be provided")
    fun hasAtLeastOneField(): Boolean =
        clockInTime != null || clockOutTime != null || userId != null
}
