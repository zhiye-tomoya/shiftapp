package com.example.shiftapp.dto.request

import jakarta.validation.constraints.AssertTrue
import jakarta.validation.constraints.NotNull
import java.time.LocalDateTime

/**
 * Request DTO for creating a new shift.
 *
 * This is what a client sends when they want to create a new shift in DRAFT status.
 * Times are expected as ISO-8601 LocalDateTime (e.g. "2025-01-15T09:00:00").
 */
data class CreateShiftRequest(
    @field:NotNull(message = "User ID is required")
    val userId: Long,

    @field:NotNull(message = "Clock-in time is required")
    val clockInTime: LocalDateTime,

    @field:NotNull(message = "Clock-out time is required")
    val clockOutTime: LocalDateTime,
) {
    /**
     * Cross-field validation: clock-out must be strictly after clock-in.
     * Fails fast at the boundary with HTTP 400 instead of HTTP 500 from the
     * domain `init` block.
     */
    @AssertTrue(message = "Clock-out time must be after clock-in time")
    fun isTimeRangeValid(): Boolean = clockOutTime.isAfter(clockInTime)
}
