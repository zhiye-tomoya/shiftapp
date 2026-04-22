package com.example.shiftapp.service

import com.example.shiftapp.domain.Shift
import com.example.shiftapp.repository.ShiftRepository
import org.springframework.stereotype.Service

/**
 * Thin service layer that orchestrates domain operations.
 *
 * Business logic is encapsulated in the [Shift] domain model.
 * This service is responsible for:
 *  - Retrieving domain objects from the repository
 *  - Delegating business operations to the domain model
 *  - Persisting the results
 */
@Service
class ShiftService(
    private val shiftRepository: ShiftRepository,
) {
    /**
     * Submit a DRAFT shift.
     *
     * Retrieves the shift, delegates to domain logic, and persists the result.
     *
     * @throws IllegalStateException if shift not found or invalid state transition
     */
    fun submitShift(shiftId: Long): Shift {
        val shift = shiftRepository.findById(shiftId)
            .orElseThrow { IllegalStateException("Shift not found: $shiftId") }
        val submittedShift = shift.submit()
        return shiftRepository.save(submittedShift)
    }

    /**
     * Approve a SUBMITTED shift.
     *
     * Retrieves the shift, delegates to domain logic, and persists the result.
     *
     * @throws IllegalStateException if shift not found or invalid state transition
     */
    fun approveShift(shiftId: Long): Shift {
        val shift = shiftRepository.findById(shiftId)
            .orElseThrow { IllegalStateException("Shift not found: $shiftId") }
        val approvedShift = shift.approve()
        return shiftRepository.save(approvedShift)
    }

    /**
     * Reject a SUBMITTED shift.
     *
     * Retrieves the shift, delegates to domain logic, and persists the result.
     *
     * @throws IllegalStateException if shift not found or invalid state transition
     */
    fun rejectShift(shiftId: Long): Shift {
        val shift = shiftRepository.findById(shiftId)
            .orElseThrow { IllegalStateException("Shift not found: $shiftId") }
        val rejectedShift = shift.reject()
        return shiftRepository.save(rejectedShift)
    }
}
