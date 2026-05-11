package com.example.shiftapp.dto.response

import java.time.DayOfWeek
import java.time.LocalTime

/**
 * Response DTO for [com.example.shiftapp.domain.ShiftTemplate].
 *
 * `ownerId` is exposed so the frontend can render "shared" (null) vs
 * "personal" templates differently and decide whether to show edit/delete
 * controls without re-checking server permissions on every keystroke.
 */
data class ShiftTemplateResponse(
    val id: Long,
    val name: String,
    val clockInLocalTime: LocalTime,
    val clockOutLocalTime: LocalTime,
    val daysOfWeek: Set<DayOfWeek>,
    val roleTag: String?,
    val ownerId: Long?,
    /** `true` when the template is shared (i.e. `ownerId == null`). Convenience flag. */
    val shared: Boolean,
    val version: Long,
)
