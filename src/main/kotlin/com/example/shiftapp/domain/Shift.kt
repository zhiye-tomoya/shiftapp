package com.example.shiftapp.domain

/**
 * Shift aggregate with rich domain model behavior.
 *
 * Business logic for state transitions is encapsulated within this domain model.
 * No persistence annotations are applied here — JPA/DB integration is
 * intentionally deferred until after the core business rules are proven
 * out with unit tests.
 */
data class Shift(
    val id: Long,
    val status: ShiftStatus,
) {
    /**
     * Submit a DRAFT shift.
     *
     * @return A new Shift instance with SUBMITTED status
     * @throws IllegalStateException if the shift is not in DRAFT status
     */
    fun submit(): Shift {
        check(status == ShiftStatus.DRAFT) {
            "Only DRAFT shifts can be submitted (was $status)"
        }
        return copy(status = ShiftStatus.SUBMITTED)
    }

    /**
     * Approve a SUBMITTED shift.
     *
     * @return A new Shift instance with APPROVED status
     * @throws IllegalStateException if the shift is not in SUBMITTED status
     */
    fun approve(): Shift {
        check(status == ShiftStatus.SUBMITTED) {
            "Only SUBMITTED shifts can be approved (was $status)"
        }
        return copy(status = ShiftStatus.APPROVED)
    }

    /**
     * Reject a SUBMITTED shift.
     *
     * @return A new Shift instance with REJECTED status
     * @throws IllegalStateException if the shift is not in SUBMITTED status
     */
    fun reject(): Shift {
        check(status == ShiftStatus.SUBMITTED) {
            "Only SUBMITTED shifts can be rejected (was $status)"
        }
        return copy(status = ShiftStatus.REJECTED)
    }
}
