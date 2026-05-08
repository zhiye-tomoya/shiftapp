package com.example.shiftapp.dto.request

import jakarta.validation.constraints.NotEmpty
import jakarta.validation.constraints.Size

/**
 * Request DTO for `PATCH /api/shifts/bulk/submit`.
 *
 * Promotes a batch of caller-owned DRAFT shifts to SUBMITTED. The owning user
 * is *not* part of the body — it comes from the JWT principal so a STAFF user
 * can never flip another user's shifts. Any id that doesn't belong to the
 * caller, doesn't exist, or isn't currently DRAFT is reported back in
 * `skipped` (or, with `atomic = true`, aborts the whole batch).
 *
 * Why PATCH and not POST?
 *  - Single-shift submit is `POST /api/shifts/{id}/submit` (an action endpoint
 *    on a single resource). Bulk = "partially update many resources at once",
 *    which is the textbook PATCH use case.
 *  - Keeps the URL space clean: `/bulk/submit` reads as "the submit verb in
 *    the bulk namespace" without colliding with `/{id}/submit`.
 */
data class BulkSubmitShiftRequest(
    @field:NotEmpty(message = "shiftIds must not be empty")
    @field:Size(max = 200, message = "shiftIds must contain at most 200 ids")
    val shiftIds: List<Long>,

    /** When `true`, ANY skip aborts the whole batch and rolls back. */
    val atomic: Boolean = false,
)
