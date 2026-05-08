package com.example.shiftapp.dto.request

import jakarta.validation.constraints.AssertTrue
import jakarta.validation.constraints.NotNull
import java.time.LocalDateTime

/**
 * Request DTO for `PUT /api/shifts/{id}` — full replacement of the editable
 * fields of a Shift.
 *
 * "Full replacement" here means *of the editable surface* — `id`, `status`
 * and `version` are not editable through this endpoint:
 *  - `id` is the path parameter
 *  - `status` is owned by the explicit lifecycle endpoints
 *    (`/submit`, `/approve`, `/reject`)
 *  - `version` is optimistic-lock metadata, not user-facing data
 *
 * Because PUT is supposed to be idempotent and to fully describe the resource,
 * every editable field is **required**. If you only want to change one field,
 * use `PATCH /api/shifts/{id}` ([UpdateShiftRequest]) instead.
 *
 * Permission matrix is enforced server-side (see [com.example.shiftapp.service.ShiftService.updateShift]):
 *  - STAFF may PUT only their own DRAFT shift, and the `userId` they send must
 *    equal the persisted owner (no self-reassign, no foreign-write).
 *  - ADMIN may PUT any shift in any status, and may reassign `userId`.
 *
 * Optimistic concurrency: [version] is optional but recommended; when present
 * it must equal the persisted row's version or the request is rejected with
 * 409 *before* any write happens. Even when omitted, JPA's `@Version` check
 * fires at flush as a backstop.
 */
data class ReplaceShiftRequest(
    @field:NotNull(message = "clockInTime is required")
    val clockInTime: LocalDateTime,

    @field:NotNull(message = "clockOutTime is required")
    val clockOutTime: LocalDateTime,

    /**
     * The owning user. STAFF callers must echo the existing owner here
     * (any other value is rejected as a permission violation server-side).
     * ADMIN callers may use this field to reassign ownership.
     */
    @field:NotNull(message = "userId is required")
    val userId: Long,

    /** Optimistic-lock version snapshot the client last read. Optional. */
    val version: Long? = null,
) {
    @AssertTrue(message = "clockOutTime must be after clockInTime")
    fun isTimeRangeValid(): Boolean = clockOutTime.isAfter(clockInTime)
}
