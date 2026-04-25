package com.example.shiftapp.domain

import jakarta.persistence.*
import java.time.Duration
import java.time.LocalDateTime
import java.time.LocalTime

/**
 * Shift aggregate with rich domain model behavior.
 *
 * Business logic for state transitions is encapsulated within this domain model.
 * JPA annotations added for persistence, but domain logic remains unchanged.
 *
 * Invariants:
 *  - `clockOutTime` must be strictly after `clockInTime`
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

    @Column(name = "clock_in_time", nullable = false)
    val clockInTime: LocalDateTime,

    @Column(name = "clock_out_time", nullable = false)
    val clockOutTime: LocalDateTime,
) {
    init {
        require(clockOutTime.isAfter(clockInTime)) {
            "Clock-out time must be after clock-in time (was $clockInTime - $clockOutTime)"
        }
    }

    fun durationMinutes(): Long {
        return Duration.between(clockInTime, clockOutTime).toMinutes()
    }

    fun isOverlapping(other: Shift): Boolean {
        return this.userId == other.userId &&
                this.clockInTime < other.clockOutTime &&
                this.clockOutTime > other.clockInTime
    }

    fun isNightShift(): Boolean {
        val nightStart = LocalTime.of(22, 0)
        val earlyMorning = LocalTime.of(6, 0)
        return clockInTime.toLocalTime().isBefore(earlyMorning) ||
                clockOutTime.toLocalTime().isAfter(nightStart) ||
                clockOutTime.toLocalDate().isAfter(clockInTime.toLocalDate())
    }

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
