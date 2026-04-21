package com.example.shiftapp.domain

/**
 * Lifecycle states for a [ShiftRequest].
 * 
 * Implements a 2-step approval process:
 * 1. Target user approves: PENDING → TARGET_APPROVED
 * 2. Admin approves: TARGET_APPROVED → ADMIN_APPROVED (final)
 * 
 * Either target user or admin can reject at their respective stage.
 * Shift ownership only transfers on final ADMIN_APPROVED status.
 */
enum class RequestStatus {
    PENDING,
    TARGET_APPROVED,  // Target user has approved, awaiting admin approval (ownership NOT yet transferred)
    ADMIN_APPROVED,   // Final approval by admin (ownership transferred to target user)
    TARGET_REJECTED,  // Target user declined the request
    ADMIN_REJECTED,   // Admin rejected after target approval
}
