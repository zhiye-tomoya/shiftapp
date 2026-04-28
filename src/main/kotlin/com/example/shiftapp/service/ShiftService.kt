package com.example.shiftapp.service

import com.example.shiftapp.domain.Shift
import com.example.shiftapp.domain.ShiftStatus
import com.example.shiftapp.repository.ShiftRepository
import com.example.shiftapp.repository.ShiftSpecifications
import org.springframework.data.domain.Page
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Pageable
import org.springframework.data.domain.Sort
import org.springframework.stereotype.Service
import java.time.LocalDateTime

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
     * List shifts for the ADMIN overview with optional filters, pagination,
     * and sorting.
     *
     * All filters are optional and AND-combined via [ShiftSpecifications].
     *  - [status] / [userId]: equality filters
     *  - [from]   inclusive lower bound on `clockInTime`
     *  - [to]     inclusive upper bound on `clockInTime`
     *
     * The [pageable] sort is sanitized against [SORTABLE_FIELDS] so that
     * callers can't (a) accidentally trigger a JPA error on an unknown
     * property or (b) sort by a field we'd rather keep internal. Anything
     * unknown is dropped; if no valid sort orders remain we fall back to
     * [DEFAULT_SORT] (`clockInTime DESC`) so results are deterministic.
     */
    fun getAllShifts(
        status: ShiftStatus?,
        userId: Long?,
        from: LocalDateTime?,
        to: LocalDateTime?,
        pageable: Pageable,
    ): Page<Shift> {
        val spec = ShiftSpecifications.allOf(
            ShiftSpecifications.hasStatus(status),
            ShiftSpecifications.hasUserId(userId),
            ShiftSpecifications.clockInFrom(from),
            ShiftSpecifications.clockInTo(to),
        )
        return shiftRepository.findAll(spec, sanitizePageable(pageable))
    }

    /**
     * Drop sort orders that reference fields outside [SORTABLE_FIELDS]
     * and substitute [DEFAULT_SORT] when nothing valid remains.
     */
    private fun sanitizePageable(pageable: Pageable): Pageable {
        val safeOrders = pageable.sort.toList()
            .filter { it.property in SORTABLE_FIELDS }
        val effectiveSort = if (safeOrders.isEmpty()) DEFAULT_SORT else Sort.by(safeOrders)
        return PageRequest.of(pageable.pageNumber, pageable.pageSize, effectiveSort)
    }

    companion object {
        /**
         * Whitelisted entity property names that clients are allowed to sort on
         * via `?sort=field,asc|desc`. Anything else is silently ignored.
         */
        internal val SORTABLE_FIELDS = setOf(
            "id",
            "status",
            "userId",
            "clockInTime",
            "clockOutTime",
        )

        /**
         * Default ordering when the caller doesn't supply a (valid) `sort`.
         * Newest shifts first matches the typical ADMIN list/calendar UI.
         */
        internal val DEFAULT_SORT: Sort = Sort.by(Sort.Direction.DESC, "clockInTime")
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
