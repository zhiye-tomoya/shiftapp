package com.example.shiftapp.service

import com.example.shiftapp.domain.Shift
import com.example.shiftapp.domain.ShiftStatus
import com.example.shiftapp.repository.ShiftRepository
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.util.Optional
import kotlin.test.assertEquals

class ShiftServiceTest {

    private val shiftRepository: ShiftRepository = mockk()
    private val shiftService = ShiftService(shiftRepository)

    @Test
    fun should_change_status_to_submitted_when_draft_shift_is_submitted() {
        val shiftId = 1L
        every { shiftRepository.findById(shiftId) } returns Optional.of(Shift(shiftId, ShiftStatus.DRAFT, userId = 100L))
        every { shiftRepository.save(any()) } answers { firstArg() }

        val result = shiftService.submitShift(shiftId)

        assertEquals(ShiftStatus.SUBMITTED, result.status)
    }

    @Test
    fun should_throw_exception_when_submitting_non_draft_shift() {
        val shiftId = 2L
        every { shiftRepository.findById(shiftId) } returns Optional.of(Shift(shiftId, ShiftStatus.SUBMITTED, userId = 100L))

        assertThrows<IllegalStateException> {
            shiftService.submitShift(shiftId)
        }
    }

    @Test
    fun should_throw_exception_when_reapproving_already_approved_shift() {
        val shift = Shift(1, ShiftStatus.APPROVED, userId = 100L)
        every { shiftRepository.findById(1) } returns Optional.of(shift)

        assertThrows<IllegalStateException> {
            shiftService.approveShift(1)
            
        }
    }

    @Test
    fun should_change_status_to_approved_when_submitted_shift_is_approved() {
        val shift = Shift(1, ShiftStatus.SUBMITTED, userId = 100L)
        every { shiftRepository.findById(1) } returns Optional.of(shift)
        every { shiftRepository.save(any()) } answers { firstArg() }

        val result = shiftService.approveShift(1)

        assertEquals(ShiftStatus.APPROVED, result.status)
    }

    @Test
    fun should_allow_rejecting_submitted_shift() {
        val shift = Shift(1, ShiftStatus.SUBMITTED, userId = 100L)
        every { shiftRepository.findById(1) } returns Optional.of(shift)
        every { shiftRepository.save(any()) } answers { firstArg() }

        val result = shiftService.rejectShift(1)

        assertEquals(ShiftStatus.REJECTED, result.status)
    }
}
