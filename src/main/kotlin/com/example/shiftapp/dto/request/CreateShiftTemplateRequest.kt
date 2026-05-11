package com.example.shiftapp.dto.request

import jakarta.validation.constraints.AssertTrue
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotEmpty
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Size
import java.time.DayOfWeek
import java.time.LocalTime

/**
 * Request DTO for `POST /api/shift-templates`.
 *
 * Templates are deliberately ownership-aware:
 *  - STAFF callers create a **personal** template (server stamps `ownerId`
 *    from the JWT). The [shared] flag, if sent, is ignored for STAFF — they
 *    cannot create global templates.
 *  - ADMIN callers may set `shared = true` to create a **global** template
 *    (`ownerId = null`). Default remains personal so an admin who forgets
 *    to set the flag doesn't accidentally publish their experiment.
 *
 * Why the field is named [shared] and not `global`: from the API consumer's
 * point of view, "shared with everyone" is what they actually toggle. The
 * domain-internal name is `isGlobal()` which mirrors `ownerId == null`.
 */
data class CreateShiftTemplateRequest(
    @field:NotBlank(message = "name is required")
    @field:Size(max = 100, message = "name must be at most 100 characters")
    val name: String,

    @field:NotNull(message = "clockInLocalTime is required")
    val clockInLocalTime: LocalTime,

    @field:NotNull(message = "clockOutLocalTime is required")
    val clockOutLocalTime: LocalTime,

    @field:NotEmpty(message = "daysOfWeek must not be empty")
    val daysOfWeek: Set<DayOfWeek>,

    /** Optional free-form tag — see [com.example.shiftapp.domain.ShiftTemplate.roleTag]. */
    @field:Size(max = 50, message = "roleTag must be at most 50 characters")
    val roleTag: String? = null,

    /** ADMIN-only flag to create a global template visible to everyone. */
    val shared: Boolean = false,
) {
    @AssertTrue(message = "clockOutLocalTime must be after clockInLocalTime")
    fun isTimeRangeValid(): Boolean = clockOutLocalTime.isAfter(clockInLocalTime)
}
