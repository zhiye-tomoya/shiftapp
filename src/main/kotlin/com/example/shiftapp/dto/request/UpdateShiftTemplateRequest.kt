package com.example.shiftapp.dto.request

import jakarta.validation.constraints.AssertTrue
import jakarta.validation.constraints.Size
import java.time.DayOfWeek
import java.time.LocalTime

/**
 * Request DTO for `PATCH /api/shift-templates/{id}` (partial update).
 *
 * Every field is optional; the service merges with the persisted template
 * and validates the merged result. We require at least one field via
 * [hasAtLeastOneFieldSet] so the client doesn't send a no-op request.
 *
 * Ownership is **not** editable here — promoting a personal template to a
 * global one (or vice versa) is a separate concept that we'll add as a
 * dedicated endpoint when needed. Keeping it out of PATCH avoids subtle
 * permission-escalation paths.
 *
 * Optimistic concurrency: include [version] (the value you got from the
 * read) and the service will 409 if the row has since changed. Omit it and
 * Hibernate's `@Version` column still acts as a backstop at flush.
 */
data class UpdateShiftTemplateRequest(
    @field:Size(max = 100, message = "name must be at most 100 characters")
    val name: String? = null,

    val clockInLocalTime: LocalTime? = null,

    val clockOutLocalTime: LocalTime? = null,

    val daysOfWeek: Set<DayOfWeek>? = null,

    @field:Size(max = 50, message = "roleTag must be at most 50 characters")
    val roleTag: String? = null,

    val version: Long? = null,
) {
    @AssertTrue(message = "At least one of name, clockInLocalTime, clockOutLocalTime, daysOfWeek, roleTag must be provided")
    fun hasAtLeastOneFieldSet(): Boolean =
        name != null || clockInLocalTime != null || clockOutLocalTime != null ||
                daysOfWeek != null || roleTag != null

    @AssertTrue(message = "daysOfWeek must not be empty when provided")
    fun isDaysOfWeekShapeValid(): Boolean =
        daysOfWeek == null || daysOfWeek.isNotEmpty()

    /**
     * If both time fields are sent together they must form a valid window.
     * (If only one is sent we can't check here — the service does the merge
     * check against the persisted value.)
     */
    @AssertTrue(message = "clockOutLocalTime must be after clockInLocalTime")
    fun isTimeRangeValid(): Boolean {
        if (clockInLocalTime == null || clockOutLocalTime == null) return true
        return clockOutLocalTime.isAfter(clockInLocalTime)
    }
}
