package com.example.shiftapp.service

import com.example.shiftapp.domain.Shift
import com.example.shiftapp.domain.ShiftStatus
import com.example.shiftapp.repository.ShiftRepository
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
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
     * Create a new shift in DRAFT status.
     */
    fun createShift(shift: Shift): Shift {
        return shiftRepository.save(shift)
    }

    /**
     * List shifts for the ADMIN overview, with optional filters and pagination.
     *
     * Both `status` and `userId` are optional. Spring Data JPA can't compose
     * dynamic predicates from method names, so we dispatch by hand to the
     * narrowest derived query that matches the supplied filters. This keeps
     * the repository surface trivially testable (no Specifications) at the
     * cost of a small switch here.
     */
    fun getAllShifts(
        status: ShiftStatus?,
        userId: Long?,
        pageable: Pageable,
    ): Page<Shift> {
        return when {
            status != null && userId != null ->
                shiftRepository.findAllByStatusAndUserId(status, userId, pageable)
            status != null ->
                shiftRepository.findAllByStatus(status, pageable)
            userId != null ->
                shiftRepository.findAllByUserId(userId, pageable)
            else ->
                shiftRepository.findAll(pageable)
        }
    }

    /**
     * Get a shift by ID.
     *
     * @throws IllegalStateException if shift not found
     */
    fun getShiftById(shiftId: Long): Shift {
        return shiftRepository.findById(shiftId)
            .orElseThrow { IllegalStateException("Shift not found: $shiftId") }
    }

    /**
     * Get all shifts for a specific user.
     */
    fun getShiftsByUserId(userId: Long): List<Shift> {
        return shiftRepository.findAllByUserId(userId)
    }

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
