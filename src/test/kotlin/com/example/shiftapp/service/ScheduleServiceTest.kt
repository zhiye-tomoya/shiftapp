package com.example.shiftapp.service

import com.example.shiftapp.domain.Shift
import com.example.shiftapp.domain.ShiftStatus
import com.example.shiftapp.dto.response.SkippedPublishReason
import com.example.shiftapp.repository.ShiftRepository
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.domain.Specification
import java.time.LocalDateTime
import java.time.YearMonth
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Unit tests for [ScheduleService] (TODO §4 — publish flow).
 *
 * The repository is mocked with mockk so we can isolate the orchestration
 * logic: window calculation, status filtering, partial-success collection,
 * and atomic-mode rollback. End-to-end coverage (RBAC, HTTP shape, real DB)
 * lives in `SchedulesControllerIntegrationTest`.
 */
class ScheduleServiceTest {

    private val shiftRepository: ShiftRepository = mockk()
    private val scheduleService = ScheduleService(shiftRepository)

    private fun shift(
        id: Long,
        status: ShiftStatus,
        clockIn: LocalDateTime,
        userId: Long = 100L,
    ): Shift = Shift(
        id = id,
        status = status,
        userId = userId,
        clockInTime = clockIn,
        clockOutTime = clockIn.plusHours(8),
    )

    /** Stub `findAll(Specification, Pageable)` to return [content]. */
    private fun stubFindAll(content: List<Shift>) {
        every {
            shiftRepository.findAll(any<Specification<Shift>>(), any<Pageable>())
        } returns PageImpl(content)
    }

    // -----------------------------------------------------------------
    // publishMonth — happy path
    // -----------------------------------------------------------------

    @Test
    fun `publishMonth should flip every APPROVED shift in the month to PUBLISHED`() {
        val approved = listOf(
            shift(1L, ShiftStatus.APPROVED, LocalDateTime.of(2025, 5, 5, 9, 0)),
            shift(2L, ShiftStatus.APPROVED, LocalDateTime.of(2025, 5, 15, 9, 0)),
            shift(3L, ShiftStatus.APPROVED, LocalDateTime.of(2025, 5, 25, 9, 0)),
        )
        stubFindAll(approved)
        val savedSlot = slot<List<Shift>>()
        every { shiftRepository.saveAll(capture(savedSlot)) } answers { firstArg<List<Shift>>() }

        val outcome = scheduleService.publishMonth(YearMonth.of(2025, 5))

        assertEquals(3, outcome.published.size)
        assertTrue(outcome.skipped.isEmpty())
        assertTrue(outcome.published.all { it.status == ShiftStatus.PUBLISHED })
        assertEquals(YearMonth.of(2025, 5), outcome.yearMonth)
        assertEquals(3, savedSlot.captured.size)
    }

    @Test
    fun `publishMonth should be a no-op when no APPROVED shifts exist`() {
        stubFindAll(emptyList())
        every { shiftRepository.saveAll(any<List<Shift>>()) } answers { firstArg<List<Shift>>() }

        val outcome = scheduleService.publishMonth(YearMonth.of(2025, 5))

        assertTrue(outcome.published.isEmpty())
        assertTrue(outcome.skipped.isEmpty())
        verify { shiftRepository.saveAll(emptyList<Shift>()) }
    }

    // -----------------------------------------------------------------
    // publishMonth — defensive skip on non-APPROVED (concurrent edit race)
    // -----------------------------------------------------------------

    @Test
    fun `publishMonth should skip non-APPROVED shifts with INVALID_STATUS_TRANSITION`() {
        // The spec should already exclude non-APPROVED, but a concurrent
        // transaction could change the status between fetch and check.
        // Feeding in a mixed list proves the in-memory guard works.
        val mixed = listOf(
            shift(1L, ShiftStatus.APPROVED, LocalDateTime.of(2025, 5, 5, 9, 0)),
            shift(2L, ShiftStatus.DRAFT,    LocalDateTime.of(2025, 5, 15, 9, 0)),
        )
        stubFindAll(mixed)
        every { shiftRepository.saveAll(any<List<Shift>>()) } answers { firstArg<List<Shift>>() }

        val outcome = scheduleService.publishMonth(YearMonth.of(2025, 5))

        assertEquals(1, outcome.published.size)
        assertEquals(1L, outcome.published[0].id)
        assertEquals(1, outcome.skipped.size)
        assertEquals(2L, outcome.skipped[0].shiftId)
        assertEquals(SkippedPublishReason.INVALID_STATUS_TRANSITION, outcome.skipped[0].reason)
    }

    // -----------------------------------------------------------------
    // publishMonth — atomic mode
    // -----------------------------------------------------------------

    @Test
    fun `publishMonth with atomic=true should throw when any shift would be skipped`() {
        val mixed = listOf(
            shift(1L, ShiftStatus.APPROVED, LocalDateTime.of(2025, 5, 5, 9, 0)),
            shift(2L, ShiftStatus.DRAFT,    LocalDateTime.of(2025, 5, 15, 9, 0)),
        )
        stubFindAll(mixed)

        assertThrows<IllegalStateException> {
            scheduleService.publishMonth(YearMonth.of(2025, 5), atomic = true)
        }
    }

    @Test
    fun `publishMonth with atomic=true should succeed when no skips occur`() {
        val approved = listOf(
            shift(1L, ShiftStatus.APPROVED, LocalDateTime.of(2025, 5, 5, 9, 0)),
        )
        stubFindAll(approved)
        every { shiftRepository.saveAll(any<List<Shift>>()) } answers { firstArg<List<Shift>>() }

        val outcome = scheduleService.publishMonth(YearMonth.of(2025, 5), atomic = true)

        assertEquals(1, outcome.published.size)
        assertTrue(outcome.skipped.isEmpty())
    }

    // -----------------------------------------------------------------
    // summarize
    // -----------------------------------------------------------------

    @Test
    fun `summarize should count shifts by status for the month`() {
        val shifts = listOf(
            shift(1L, ShiftStatus.DRAFT,     LocalDateTime.of(2025, 5, 1, 9, 0)),
            shift(2L, ShiftStatus.DRAFT,     LocalDateTime.of(2025, 5, 2, 9, 0)),
            shift(3L, ShiftStatus.SUBMITTED, LocalDateTime.of(2025, 5, 3, 9, 0)),
            shift(4L, ShiftStatus.APPROVED,  LocalDateTime.of(2025, 5, 4, 9, 0)),
            shift(5L, ShiftStatus.APPROVED,  LocalDateTime.of(2025, 5, 5, 9, 0)),
            shift(6L, ShiftStatus.APPROVED,  LocalDateTime.of(2025, 5, 6, 9, 0)),
            shift(7L, ShiftStatus.REJECTED,  LocalDateTime.of(2025, 5, 7, 9, 0)),
            shift(8L, ShiftStatus.PUBLISHED, LocalDateTime.of(2025, 5, 8, 9, 0)),
        )
        stubFindAll(shifts)

        val summary = scheduleService.summarize(YearMonth.of(2025, 5))

        assertEquals(YearMonth.of(2025, 5), summary.yearMonth)
        assertEquals(2L, summary.draft)
        assertEquals(1L, summary.submitted)
        assertEquals(3L, summary.approved)
        assertEquals(1L, summary.rejected)
        assertEquals(1L, summary.published)
        assertEquals(8L, summary.total)
    }

    @Test
    fun `summarize should return zeros for an empty month`() {
        stubFindAll(emptyList())

        val summary = scheduleService.summarize(YearMonth.of(2025, 5))

        assertEquals(0L, summary.draft)
        assertEquals(0L, summary.submitted)
        assertEquals(0L, summary.approved)
        assertEquals(0L, summary.rejected)
        assertEquals(0L, summary.published)
        assertEquals(0L, summary.total)
    }
}
