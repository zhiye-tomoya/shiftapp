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
 *
 * Concurrency:
 *  - [version] is a JPA `@Version` field. Hibernate auto-increments it on every
 *    UPDATE and refuses to apply an UPDATE whose `version` no longer matches the
 *    DB row, raising `OptimisticLockingFailureException` (mapped to HTTP 409 by
 *    the global exception handler). Callers can supply the version they read in
 *    `PUT /api/shifts/{id}` to defend against lost updates between two editors.
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

    @Version
    @Column(nullable = false)
    val version: Long = 0L,
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

    /**
     * Publish an APPROVED shift.
     *
     * Driven by the monthly publish flow (`POST /api/schedules/{yyyy-MM}/publish`).
     * Only APPROVED shifts are eligible — DRAFT/SUBMITTED have not yet been
     * blessed by an ADMIN, and re-publishing an already-PUBLISHED shift would
     * silently double-emit any future "schedule published" notifications, so we
     * make the no-op explicit and let the orchestrator skip it with a typed
     * reason instead.
     *
     * @return A new Shift instance with PUBLISHED status
     * @throws IllegalStateException if the shift is not in APPROVED status
     */
    fun publish(): Shift {
        check(status == ShiftStatus.APPROVED) {
            "Only APPROVED shifts can be published (was $status)"
        }
        return copy(status = ShiftStatus.PUBLISHED)
    }
}
