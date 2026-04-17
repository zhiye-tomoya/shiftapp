package com.example.shiftapp.service

import com.example.shiftapp.domain.Shift
import com.example.shiftapp.domain.ShiftStatus
import com.example.shiftapp.repository.ShiftRepository
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import kotlin.test.assertEquals

/**
 * Pure unit test for [ShiftService.submitShift].
 *
 * - No Spring context, no database.
 * - [ShiftRepository] is mocked with MockK.
 * - Only the business rules are exercised:
 *     1. DRAFT         → can be submitted, becomes SUBMITTED.
 *     2. Non-DRAFT     → cannot be submitted, throws.
 */
class ShiftServiceTest {

    private val shiftRepository: ShiftRepository = mockk()
    private val shiftService = ShiftService(shiftRepository)

    @Test
    fun `DRAFTのシフトを提出するとSUBMITTEDになる`() {
        val shiftId = 1L
        every { shiftRepository.findById(shiftId) } returns Shift(shiftId, ShiftStatus.DRAFT)
        every { shiftRepository.save(any()) } answers { firstArg() }

        val result = shiftService.submitShift(shiftId)

        assertEquals(ShiftStatus.SUBMITTED, result.status)
    }

    @Test
    fun `DRAFT以外のシフトを提出しようとすると例外が発生する`() {
        val shiftId = 2L
        every { shiftRepository.findById(shiftId) } returns Shift(shiftId, ShiftStatus.SUBMITTED)

        assertThrows<IllegalStateException> {
            shiftService.submitShift(shiftId)
        }
    }
}
