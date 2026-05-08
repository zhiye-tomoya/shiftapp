package com.example.shiftapp.service

import com.example.shiftapp.domain.Shift
import com.example.shiftapp.domain.ShiftStatus
import com.example.shiftapp.dto.request.BulkCreateShiftRequest
import com.example.shiftapp.dto.request.BulkSubmitShiftRequest
import com.example.shiftapp.dto.response.SkippedShift
import com.example.shiftapp.dto.response.SkippedShiftReason
import com.example.shiftapp.dto.response.SkippedSubmit
import com.example.shiftapp.dto.response.SkippedSubmitReason
import com.example.shiftapp.repository.ShiftRepository
import com.example.shiftapp.repository.ShiftSpecifications
import org.springframework.data.domain.Page
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Pageable
import org.springframework.data.domain.Sort
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
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
    fun getShiftsByUserId(userId: Long, status: ShiftStatus?): List<Shift> {
        return if (status != null) {
                        shiftRepository.findByUserIdAndStatus(userId, status)
                    } else {
                        shiftRepository.findAllByUserId(userId)
                    }
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

    // -----------------------------------------------------------------
    // Bulk operations
    // -----------------------------------------------------------------

    /**
     * Create many DRAFT shifts for [userId] in one transaction.
     *
     * Algorithm:
     *  1. Expand the (`startDate`, `endDate`, `daysOfWeek`, time window) into a
     *     list of `Shift` candidates, all owned by [userId] and all DRAFT.
     *  2. Fetch every existing shift for [userId] inside that calendar window
     *     in **one** query — overlap detection is done in-memory afterwards
     *     so we don't issue N round-trips.
     *  3. For each candidate, mark it as either created or skipped:
     *     - overlap with existing OR with an earlier candidate in the same
     *       request → [SkippedShiftReason.OVERLAPPING_EXISTING_SHIFT]
     *     - if `request.skipOverlapping == false`, the first overlap raises
     *       instead of being collected (caller wants strict mode).
     *  4. If `request.atomic == true` and anything was skipped, raise so that
     *     `@Transactional` rolls the whole batch back — the caller never sees
     *     a half-applied result.
     *  5. `saveAll` the survivors.
     *
     * Note on layering:
     *  We deliberately reuse the API-facing [SkippedShift]/[SkippedShiftReason]
     *  types here. They are pure value objects (no entity behaviour) and the
     *  reason set is exactly the same in both layers, so duplicating them
     *  would only invite drift.
     */
    @Transactional
    fun bulkCreate(userId: Long, request: BulkCreateShiftRequest): BulkCreateOutcome {
        // 1. Expand candidates from the (range × days-of-week × time-window) spec.
        val candidates = expandCandidates(userId, request)
        if (candidates.isEmpty()) {
            return BulkCreateOutcome(created = emptyList(), skipped = emptyList())
        }

        // 2. Pull existing shifts for this user in the same calendar window in one query.
        //    Use [endDate + 1 day, exclusive) on the upper end so a shift starting at
        //    23:59 on the last day still falls inside the window.
        val rangeStart = request.startDate.atStartOfDay()
        val rangeEnd = request.endDate.plusDays(1).atStartOfDay()
        val existing = shiftRepository.findAllByUserIdAndClockInTimeBetween(
            userId, rangeStart, rangeEnd,
        )

        // 3. Walk candidates and partition into created / skipped.
        val toCreate = mutableListOf<Shift>()
        val skipped = mutableListOf<SkippedShift>()
        for (candidate in candidates) {
            val overlapsExisting = existing.any { it.isOverlapping(candidate) }
            // Also defend against overlapping candidates *within* the same request,
            // e.g. duplicate days-of-week entries or future overnight support.
            val overlapsBatch = toCreate.any { it.isOverlapping(candidate) }

            if (overlapsExisting || overlapsBatch) {
                if (!request.skipOverlapping) {
                    throw IllegalStateException(
                        "Overlapping shift exists for date ${candidate.clockInTime.toLocalDate()}"
                    )
                }
                skipped += SkippedShift(
                    date = candidate.clockInTime.toLocalDate(),
                    reason = SkippedShiftReason.OVERLAPPING_EXISTING_SHIFT,
                )
            } else {
                toCreate += candidate
            }
        }

        // 4. Atomic mode: any skip → roll the whole batch back.
        if (request.atomic && skipped.isNotEmpty()) {
            throw IllegalStateException(
                "Atomic bulk-create failed: ${skipped.size} day(s) would be skipped"
            )
        }

        // 5. Persist the survivors.
        val saved = shiftRepository.saveAll(toCreate).toList()
        return BulkCreateOutcome(created = saved, skipped = skipped)
    }

    /**
     * Iterate every calendar day in [startDate, endDate], keep those whose
     * `dayOfWeek` is in `daysOfWeek` (or all of them if it's null), and build
     * an in-memory `Shift(DRAFT)` for each.
     */
    private fun expandCandidates(userId: Long, request: BulkCreateShiftRequest): List<Shift> {
        val targetDays = request.daysOfWeek
        return generateSequence(request.startDate) { it.plusDays(1) }
            .takeWhile { !it.isAfter(request.endDate) }
            .filter { targetDays == null || it.dayOfWeek in targetDays }
            .map { date ->
                Shift(
                    userId = userId,
                    status = ShiftStatus.DRAFT,
                    clockInTime = date.atTime(request.clockInLocalTime),
                    clockOutTime = date.atTime(request.clockOutLocalTime),
                )
            }
            .toList()
    }

    /**
     * Bulk DRAFT → SUBMITTED for shifts owned by [userId].
     *
     * Each id is classified independently:
     *  - missing          → [SkippedSubmitReason.NOT_FOUND]
     *  - someone else's   → [SkippedSubmitReason.NOT_OWNED_BY_REQUESTER]
     *                       (IDOR defence — never silently flip another
     *                       user's shift)
     *  - not DRAFT        → [SkippedSubmitReason.INVALID_STATUS_TRANSITION]
     *  - else             → `shift.submit()` → queued for save
     *
     * `request.atomic == true` raises on any skip so `@Transactional`
     * rolls the whole batch back.
     */
    @Transactional
    fun bulkSubmit(userId: Long, request: BulkSubmitShiftRequest): BulkSubmitOutcome {
        val ids = request.shiftIds
        // findAllById silently drops missing ids — we put them back manually
        // so the response can tell the caller which ids vanished.
        val foundById = shiftRepository.findAllById(ids).associateBy { it.id }

        val toSave = mutableListOf<Shift>()
        val skipped = mutableListOf<SkippedSubmit>()

        for (id in ids) {
            val shift = foundById[id]
            when {
                shift == null ->
                    skipped += SkippedSubmit(id, SkippedSubmitReason.NOT_FOUND)

                shift.userId != userId ->
                    skipped += SkippedSubmit(id, SkippedSubmitReason.NOT_OWNED_BY_REQUESTER)

                shift.status != ShiftStatus.DRAFT ->
                    skipped += SkippedSubmit(id, SkippedSubmitReason.INVALID_STATUS_TRANSITION)

                else ->
                    // Domain enforces "only DRAFT can submit" itself; we just gate-keep
                    // ahead of time so the response carries a typed reason.
                    toSave += shift.submit()
            }
        }

        if (request.atomic && skipped.isNotEmpty()) {
            throw IllegalStateException(
                "Atomic bulk-submit failed: ${skipped.size} id(s) would be skipped"
            )
        }

        val saved = shiftRepository.saveAll(toSave).toList()
        return BulkSubmitOutcome(submitted = saved, skipped = skipped)
    }
}

/**
 * Service-level outcome of a successful bulk-create. The controller maps this
 * into the API DTO ([com.example.shiftapp.dto.response.BulkCreateShiftResponse]).
 */
data class BulkCreateOutcome(
    val created: List<Shift>,
    val skipped: List<SkippedShift>,
)

/**
 * Service-level outcome of a successful bulk-submit. The controller maps this
 * into the API DTO ([com.example.shiftapp.dto.response.BulkSubmitShiftResponse]).
 */
data class BulkSubmitOutcome(
    val submitted: List<Shift>,
    val skipped: List<SkippedSubmit>,
)

