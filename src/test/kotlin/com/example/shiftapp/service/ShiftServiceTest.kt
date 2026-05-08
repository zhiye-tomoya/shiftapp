package com.example.shiftapp.service

import com.example.shiftapp.domain.Shift
import com.example.shiftapp.domain.ShiftStatus
import com.example.shiftapp.dto.request.BulkCreateShiftRequest
import com.example.shiftapp.dto.request.BulkSubmitShiftRequest
import com.example.shiftapp.dto.response.SkippedShiftReason
import com.example.shiftapp.dto.response.SkippedSubmitReason
import com.example.shiftapp.repository.ShiftRepository
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.util.Optional
import kotlin.test.assertEquals
import kotlin.test.assertTrue


class ShiftServiceTest {

    private val shiftRepository: ShiftRepository = mockk()
    private val shiftService = ShiftService(shiftRepository)

    private val clockIn: LocalDateTime = LocalDateTime.of(2025, 1, 15, 9, 0)
    private val clockOut: LocalDateTime = LocalDateTime.of(2025, 1, 15, 17, 0)

    private fun shiftWith(id: Long, status: ShiftStatus, userId: Long = 100L): Shift =
        Shift(
            id = id,
            status = status,
            userId = userId,
            clockInTime = clockIn,
            clockOutTime = clockOut,
        )

    @Test
    fun should_change_status_to_submitted_when_draft_shift_is_submitted() {
        val shiftId = 1L
        every { shiftRepository.findById(shiftId) } returns Optional.of(shiftWith(shiftId, ShiftStatus.DRAFT))
        every { shiftRepository.save(any()) } answers { firstArg() }

        val result = shiftService.submitShift(shiftId)

        assertEquals(ShiftStatus.SUBMITTED, result.status)
    }

    @Test
    fun should_throw_exception_when_submitting_non_draft_shift() {
        val shiftId = 2L
        every { shiftRepository.findById(shiftId) } returns Optional.of(shiftWith(shiftId, ShiftStatus.SUBMITTED))

        assertThrows<IllegalStateException> {
            shiftService.submitShift(shiftId)
        }
    }

    @Test
    fun should_throw_exception_when_reapproving_already_approved_shift() {
        val shift = shiftWith(1L, ShiftStatus.APPROVED)
        every { shiftRepository.findById(1) } returns Optional.of(shift)

        assertThrows<IllegalStateException> {
            shiftService.approveShift(1)
        }
    }

    @Test
    fun should_change_status_to_approved_when_submitted_shift_is_approved() {
        val shift = shiftWith(1L, ShiftStatus.SUBMITTED)
        every { shiftRepository.findById(1) } returns Optional.of(shift)
        every { shiftRepository.save(any()) } answers { firstArg() }

        val result = shiftService.approveShift(1)

        assertEquals(ShiftStatus.APPROVED, result.status)
    }

    @Test
    fun should_allow_rejecting_submitted_shift() {
        val shift = shiftWith(1L, ShiftStatus.SUBMITTED)
        every { shiftRepository.findById(1) } returns Optional.of(shift)
        every { shiftRepository.save(any()) } answers { firstArg() }

        val result = shiftService.rejectShift(1)

        assertEquals(ShiftStatus.REJECTED, result.status)
    }

    // -----------------------------------------------------------------
    // bulkCreate
    // -----------------------------------------------------------------

    /** Helper: a 7-day Mon–Fri 9–18 request starting on a known Monday. */
    private fun mondayToFridayRequest(
        start: LocalDate = LocalDate.of(2025, 1, 13), // Monday
        end: LocalDate   = LocalDate.of(2025, 1, 17), // Friday
        skipOverlapping: Boolean = true,
        atomic: Boolean = false,
    ) = BulkCreateShiftRequest(
        startDate = start,
        endDate = end,
        daysOfWeek = setOf(
            DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY,
            DayOfWeek.THURSDAY, DayOfWeek.FRIDAY,
        ),
        clockInLocalTime = LocalTime.of(9, 0),
        clockOutLocalTime = LocalTime.of(18, 0),
        skipOverlapping = skipOverlapping,
        atomic = atomic,
    )

    @Test
    fun `bulkCreate should expand range × days-of-week into one DRAFT shift per matching day`() {
        // Given: no existing shifts, a Mon–Fri request (5 weekdays in the week)
        every { shiftRepository.findAllByUserIdAndClockInTimeBetween(any(), any(), any()) } returns emptyList()
        every { shiftRepository.saveAll(any<List<Shift>>()) } answers { firstArg<List<Shift>>() }

        val outcome = shiftService.bulkCreate(userId = 100L, request = mondayToFridayRequest())

        assertEquals(5, outcome.created.size)
        assertTrue(outcome.skipped.isEmpty())
        assertTrue(outcome.created.all { it.status == ShiftStatus.DRAFT })
        assertTrue(outcome.created.all { it.userId == 100L })
        // Sanity: each day's clockInTime should fall on the requested days-of-week.
        assertTrue(outcome.created.all {
            it.clockInTime.dayOfWeek in setOf(
                DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY,
                DayOfWeek.THURSDAY, DayOfWeek.FRIDAY,
            )
        })
    }

    @Test
    fun `bulkCreate should skip days that overlap an existing shift when skipOverlapping=true`() {
        // Given: an existing Wed shift in the same window
        val existingWed = shiftWith(
            id = 999L, status = ShiftStatus.APPROVED, userId = 100L,
        ).copy(
            clockInTime = LocalDateTime.of(2025, 1, 15, 9, 0),  // Wed
            clockOutTime = LocalDateTime.of(2025, 1, 15, 18, 0),
        )
        every { shiftRepository.findAllByUserIdAndClockInTimeBetween(any(), any(), any()) } returns listOf(existingWed)
        every { shiftRepository.saveAll(any<List<Shift>>()) } answers { firstArg<List<Shift>>() }

        val outcome = shiftService.bulkCreate(userId = 100L, request = mondayToFridayRequest())

        // Then: 4 created, 1 skipped (Wednesday)
        assertEquals(4, outcome.created.size)
        assertEquals(1, outcome.skipped.size)
        assertEquals(LocalDate.of(2025, 1, 15), outcome.skipped[0].date)
        assertEquals(SkippedShiftReason.OVERLAPPING_EXISTING_SHIFT, outcome.skipped[0].reason)
    }

    @Test
    fun `bulkCreate should throw when atomic=true and any day overlaps`() {
        val existingWed = shiftWith(1L, ShiftStatus.APPROVED, 100L).copy(
            clockInTime = LocalDateTime.of(2025, 1, 15, 9, 0),
            clockOutTime = LocalDateTime.of(2025, 1, 15, 18, 0),
        )
        every { shiftRepository.findAllByUserIdAndClockInTimeBetween(any(), any(), any()) } returns listOf(existingWed)

        assertThrows<IllegalStateException> {
            shiftService.bulkCreate(
                userId = 100L,
                request = mondayToFridayRequest(atomic = true),
            )
        }
    }

    @Test
    fun `bulkCreate should throw when skipOverlapping=false and any day overlaps`() {
        val existingWed = shiftWith(1L, ShiftStatus.APPROVED, 100L).copy(
            clockInTime = LocalDateTime.of(2025, 1, 15, 9, 0),
            clockOutTime = LocalDateTime.of(2025, 1, 15, 18, 0),
        )
        every { shiftRepository.findAllByUserIdAndClockInTimeBetween(any(), any(), any()) } returns listOf(existingWed)

        assertThrows<IllegalStateException> {
            shiftService.bulkCreate(
                userId = 100L,
                request = mondayToFridayRequest(skipOverlapping = false),
            )
        }
    }

    // -----------------------------------------------------------------
    // bulkSubmit
    // -----------------------------------------------------------------

    @Test
    fun `bulkSubmit should flip caller-owned DRAFT shifts to SUBMITTED`() {
        val s1 = shiftWith(1L, ShiftStatus.DRAFT, userId = 100L)
        val s2 = shiftWith(2L, ShiftStatus.DRAFT, userId = 100L)
        every { shiftRepository.findAllById(listOf(1L, 2L)) } returns listOf(s1, s2)
        every { shiftRepository.saveAll(any<List<Shift>>()) } answers { firstArg<List<Shift>>() }

        val outcome = shiftService.bulkSubmit(
            userId = 100L,
            request = BulkSubmitShiftRequest(shiftIds = listOf(1L, 2L)),
        )

        assertEquals(2, outcome.submitted.size)
        assertTrue(outcome.skipped.isEmpty())
        assertTrue(outcome.submitted.all { it.status == ShiftStatus.SUBMITTED })
    }

    @Test
    fun `bulkSubmit should classify skips into NOT_FOUND, NOT_OWNED_BY_REQUESTER and INVALID_STATUS_TRANSITION`() {
        val ownedDraft        = shiftWith(1L, ShiftStatus.DRAFT,    userId = 100L)
        val ownedAlreadySent  = shiftWith(2L, ShiftStatus.SUBMITTED, userId = 100L)
        val foreign           = shiftWith(3L, ShiftStatus.DRAFT,    userId = 999L) // someone else
        // id=4L is intentionally absent to exercise NOT_FOUND.
        every { shiftRepository.findAllById(listOf(1L, 2L, 3L, 4L)) } returns
            listOf(ownedDraft, ownedAlreadySent, foreign)
        every { shiftRepository.saveAll(any<List<Shift>>()) } answers { firstArg<List<Shift>>() }

        val outcome = shiftService.bulkSubmit(
            userId = 100L,
            request = BulkSubmitShiftRequest(shiftIds = listOf(1L, 2L, 3L, 4L)),
        )

        assertEquals(1, outcome.submitted.size)
        assertEquals(1L, outcome.submitted[0].id)

        val reasonsById = outcome.skipped.associate { it.shiftId to it.reason }
        assertEquals(SkippedSubmitReason.INVALID_STATUS_TRANSITION, reasonsById[2L])
        assertEquals(SkippedSubmitReason.NOT_OWNED_BY_REQUESTER,    reasonsById[3L])
        assertEquals(SkippedSubmitReason.NOT_FOUND,                 reasonsById[4L])
    }

    @Test
    fun `bulkSubmit should throw when atomic=true and any id is skipped`() {
        val foreign = shiftWith(1L, ShiftStatus.DRAFT, userId = 999L)
        every { shiftRepository.findAllById(listOf(1L)) } returns listOf(foreign)

        assertThrows<IllegalStateException> {
            shiftService.bulkSubmit(
                userId = 100L,
                request = BulkSubmitShiftRequest(shiftIds = listOf(1L), atomic = true),
            )
        }
    }
}

