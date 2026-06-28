package com.example.shiftapp.domain

/**
 * Lifecycle states for a [Shift].
 *
 * Transitions:
 *  - DRAFT     → SUBMITTED  via [com.example.shiftapp.service.ShiftService.submitShift]
 *  - SUBMITTED → APPROVED   via [com.example.shiftapp.service.ShiftService.approveShift]
 *  - SUBMITTED → REJECTED   via [com.example.shiftapp.service.ShiftService.rejectShift]
 *  - APPROVED  → PUBLISHED  via [com.example.shiftapp.service.ScheduleService.publishMonth]
 *
 * [PUBLISHED] is the terminal "this shift is officially on the published
 * schedule" state — STAFF can see it on their calendar, and editing/deleting
 * it is ADMIN-only (the same gate as APPROVED, intentionally; see the
 * permission matrix on [com.example.shiftapp.service.ShiftService.updateShift]).
 */
enum class ShiftStatus {
    DRAFT,
    SUBMITTED,
    APPROVED,
    REJECTED,
    PUBLISHED,
}
