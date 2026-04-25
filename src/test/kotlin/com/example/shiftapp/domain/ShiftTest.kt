package com.example.shiftapp.domain

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.time.LocalDateTime
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ShiftTest {

    /**
     * Default fixture times for tests that don't care about the specific values.
     * Using a fixed date so tests are deterministic.
     */
    private val defaultClockIn: LocalDateTime = LocalDateTime.of(2025, 1, 15, 9, 0)
    private val defaultClockOut: LocalDateTime = LocalDateTime.of(2025, 1, 15, 17, 0)

    private fun shift(
        id: Long = 1L,
        status: ShiftStatus = ShiftStatus.DRAFT,
        userId: Long = 100L,
        clockInTime: LocalDateTime = defaultClockIn,
        clockOutTime: LocalDateTime = defaultClockOut,
    ): Shift = Shift(
        id = id,
        status = status,
        userId = userId,
        clockInTime = clockInTime,
        clockOutTime = clockOutTime,
    )

    // ===== State transition tests =====

    @Test
    fun `new shift should start as DRAFT`() {
        val shift = shift(status = ShiftStatus.DRAFT)
        assertEquals(ShiftStatus.DRAFT, shift.status)
    }

    @Test
    fun `should change status to SUBMITTED when submit is called on DRAFT shift`() {
        val shift = shift(status = ShiftStatus.DRAFT)
        val submittedShift = shift.submit()
        assertEquals(ShiftStatus.SUBMITTED, submittedShift.status)
    }

    @Test
    fun `should throw exception when submit is called on non-DRAFT shift`() {
        val shift = shift(status = ShiftStatus.SUBMITTED)
        assertThrows<IllegalStateException> {
            shift.submit()
        }
    }

    @Test
    fun `should change status to APPROVED when approve is called on SUBMITTED shift`() {
        val shift = shift(status = ShiftStatus.SUBMITTED)
        val approvedShift = shift.approve()
        assertEquals(ShiftStatus.APPROVED, approvedShift.status)
    }

    @Test
    fun `should throw exception when approve is called on non-SUBMITTED shift`() {
        val shift = shift(status = ShiftStatus.APPROVED)
        assertThrows<IllegalStateException> {
            shift.approve()
        }
    }

    @Test
    fun `should change status to REJECTED when reject is called on SUBMITTED shift`() {
        val shift = shift(status = ShiftStatus.SUBMITTED)
        val rejectedShift = shift.reject()
        assertEquals(ShiftStatus.REJECTED, rejectedShift.status)
    }

    @Test
    fun `should throw exception when reject is called on non-SUBMITTED shift`() {
        val shift = shift(status = ShiftStatus.APPROVED)
        assertThrows<IllegalStateException> {
            shift.reject()
        }
    }

    @Test
    fun `should throw exception when submit is called on APPROVED shift`() {
        val shift = shift(status = ShiftStatus.APPROVED)
        assertThrows<IllegalStateException> {
            shift.submit()
        }
    }

    @Test
    fun `should throw exception when approve is called on REJECTED shift`() {
        val shift = shift(status = ShiftStatus.REJECTED)
        assertThrows<IllegalStateException> {
            shift.approve()
        }
    }

    @Test
    fun `submit should not mutate original shift`() {
        val original = shift(status = ShiftStatus.DRAFT)
        val submitted = original.submit()
        assertEquals(ShiftStatus.DRAFT, original.status)
        assertEquals(ShiftStatus.SUBMITTED, submitted.status)
    }

    @Test
    fun `approve should not mutate original shift`() {
        val original = shift(status = ShiftStatus.SUBMITTED)
        val approved = original.approve()
        assertEquals(ShiftStatus.SUBMITTED, original.status)
        assertEquals(ShiftStatus.APPROVED, approved.status)
    }

    @Test
    fun `reject should not mutate original shift`() {
        val original = shift(status = ShiftStatus.SUBMITTED)
        val rejected = original.reject()
        assertEquals(ShiftStatus.SUBMITTED, original.status)
        assertEquals(ShiftStatus.REJECTED, rejected.status)
    }

    // ===== Clock-in / clock-out invariant tests =====

    @Test
    fun `should throw when clockOutTime equals clockInTime`() {
        val now = LocalDateTime.of(2025, 1, 15, 9, 0)
        assertThrows<IllegalArgumentException> {
            shift(clockInTime = now, clockOutTime = now)
        }
    }

    @Test
    fun `should throw when clockOutTime is before clockInTime`() {
        assertThrows<IllegalArgumentException> {
            shift(
                clockInTime = LocalDateTime.of(2025, 1, 15, 17, 0),
                clockOutTime = LocalDateTime.of(2025, 1, 15, 9, 0),
            )
        }
    }

    @Test
    fun `should accept overnight shift where clockOutTime is on next day`() {
        val s = shift(
            clockInTime = LocalDateTime.of(2025, 1, 15, 22, 0),
            clockOutTime = LocalDateTime.of(2025, 1, 16, 6, 0),
        )
        assertEquals(8 * 60, s.durationMinutes())
    }

    // ===== durationMinutes() =====

    @Test
    fun `durationMinutes should compute minutes between clockIn and clockOut`() {
        val s = shift(
            clockInTime = LocalDateTime.of(2025, 1, 15, 9, 0),
            clockOutTime = LocalDateTime.of(2025, 1, 15, 17, 30),
        )
        assertEquals(8 * 60 + 30, s.durationMinutes())
    }

    // ===== isOverlapping() =====

    @Test
    fun `isOverlapping returns true when same user shifts overlap`() {
        val a = shift(
            userId = 100L,
            clockInTime = LocalDateTime.of(2025, 1, 15, 9, 0),
            clockOutTime = LocalDateTime.of(2025, 1, 15, 17, 0),
        )
        val b = shift(
            userId = 100L,
            clockInTime = LocalDateTime.of(2025, 1, 15, 13, 0),
            clockOutTime = LocalDateTime.of(2025, 1, 15, 21, 0),
        )
        assertTrue(a.isOverlapping(b))
        assertTrue(b.isOverlapping(a))
    }

    @Test
    fun `isOverlapping returns false when same user shifts are back-to-back`() {
        val a = shift(
            userId = 100L,
            clockInTime = LocalDateTime.of(2025, 1, 15, 9, 0),
            clockOutTime = LocalDateTime.of(2025, 1, 15, 13, 0),
        )
        val b = shift(
            userId = 100L,
            clockInTime = LocalDateTime.of(2025, 1, 15, 13, 0),
            clockOutTime = LocalDateTime.of(2025, 1, 15, 17, 0),
        )
        // Touching boundaries are NOT considered overlapping (strict <, >)
        assertFalse(a.isOverlapping(b))
    }

    @Test
    fun `isOverlapping returns false for different users even with same times`() {
        val a = shift(
            userId = 100L,
            clockInTime = LocalDateTime.of(2025, 1, 15, 9, 0),
            clockOutTime = LocalDateTime.of(2025, 1, 15, 17, 0),
        )
        val b = shift(
            userId = 200L,
            clockInTime = LocalDateTime.of(2025, 1, 15, 9, 0),
            clockOutTime = LocalDateTime.of(2025, 1, 15, 17, 0),
        )
        assertFalse(a.isOverlapping(b))
    }

    // ===== isNightShift() =====

    @Test
    fun `isNightShift returns false for normal day shift`() {
        val s = shift(
            clockInTime = LocalDateTime.of(2025, 1, 15, 9, 0),
            clockOutTime = LocalDateTime.of(2025, 1, 15, 17, 0),
        )
        assertFalse(s.isNightShift())
    }

    @Test
    fun `isNightShift returns true when clockIn is before 6am`() {
        val s = shift(
            clockInTime = LocalDateTime.of(2025, 1, 15, 4, 0),
            clockOutTime = LocalDateTime.of(2025, 1, 15, 12, 0),
        )
        assertTrue(s.isNightShift())
    }

    @Test
    fun `isNightShift returns true when clockOut is after 10pm`() {
        val s = shift(
            clockInTime = LocalDateTime.of(2025, 1, 15, 14, 0),
            clockOutTime = LocalDateTime.of(2025, 1, 15, 23, 0),
        )
        assertTrue(s.isNightShift())
    }

    @Test
    fun `isNightShift returns true for overnight shift crossing midnight`() {
        val s = shift(
            clockInTime = LocalDateTime.of(2025, 1, 15, 18, 0),
            clockOutTime = LocalDateTime.of(2025, 1, 16, 2, 0),
        )
        assertTrue(s.isNightShift())
    }
}
