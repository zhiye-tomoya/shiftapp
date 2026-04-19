package com.example.shiftapp.domain

/**
 * Lifecycle states for a [ShiftRequest].
 * 
 * Implements a 2-step approval process:
 * 1. Target user approves: PENDING → TARGET_APPROVED
 * 2. Admin approves: TARGET_APPROVED → ADMIN_APPROVED (final)
 * 
 * Either target user or admin can reject at any stage.
 */
enum class RequestStatus {
    PENDING,
    TARGET_APPROVED,  // Target user has approved, awaiting admin approval
    ADMIN_APPROVED,   // Final approval by admin (swap is complete)
    REJECTED,
}
