package com.example.shiftapp.domain

import jakarta.persistence.*

/**
 * Shift aggregate with rich domain model behavior.
 *
 * Business logic for state transitions is encapsulated within this domain model.
 * JPA annotations added for persistence, but domain logic remains unchanged.
 */
@Entity
@Table(name = "shifts")
data class Shift(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0L,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    val status: ShiftStatus,

    @Column(name = "user_id", nullable = false)
    val userId: Long,
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
