package com.example.shiftapp.service

import com.example.shiftapp.domain.Shift
import com.example.shiftapp.domain.ShiftStatus
import com.example.shiftapp.repository.ShiftRepository
import org.springframework.stereotype.Service

/**
 * TDD phase: this class exists ONLY so that [ShiftServiceTest] can compile.
 *
 * The real behaviour of [submitShift] is intentionally left unimplemented —
 * the tests will drive the implementation in the next step (red → green).
 */
@Service
class ShiftService(
    private val shiftRepository: ShiftRepository,
) {
    /**
     * Submit a DRAFT shift.
     *
     * Rules (see ShiftServiceTest):
     *  - DRAFT           → becomes SUBMITTED and is persisted
     *  - Any other state → IllegalStateException is thrown
     */
    fun submitShift(shiftId: Long): Shift {
        val shift = shiftRepository.findById(shiftId)
            ?: throw IllegalStateException("Shift not found: $shiftId")
        check(shift.status == ShiftStatus.DRAFT) {
            "Only DRAFT shifts can be submitted (was ${shift.status})"
        }
        return shiftRepository.save(shift.copy(status = ShiftStatus.SUBMITTED))
    }

    fun approveShift(shiftId: Long): Shift {
        val shift = shiftRepository.findById(shiftId)
            ?: throw IllegalStateException("Shift not found: $shiftId")
        check(shift.status == ShiftStatus.SUBMITTED) {
            "Only SUBMITTED shifts can be approved (was ${shift.status})"
        }
        return shiftRepository.save(shift.copy(status = ShiftStatus.APPROVED))
    }

    fun rejectShift(shiftId: Long): Shift {
        val shift = shiftRepository.findById(shiftId)
            ?: throw IllegalStateException("Shift not found: $shiftId")
        check(shift.status == ShiftStatus.SUBMITTED) {
            "Only SUBMITTED shifts can be rejected (was ${shift.status})"
        }
        return shiftRepository.save(shift.copy(status = ShiftStatus.REJECTED))
    }
}
