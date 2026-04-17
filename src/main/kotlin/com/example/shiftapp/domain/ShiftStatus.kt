package com.example.shiftapp.domain

/**
 * Lifecycle states for a [Shift].
 *
 * Only DRAFT shifts can transition to SUBMITTED via
 * [com.example.shiftapp.service.ShiftService.submitShift].
 */
enum class ShiftStatus {
    DRAFT,
    SUBMITTED,
    APPROVED,
    REJECTED,
}
