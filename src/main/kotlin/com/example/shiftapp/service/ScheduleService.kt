package com.example.shiftapp.service

import com.example.shiftapp.domain.Shift
import com.example.shiftapp.domain.ShiftStatus
import com.example.shiftapp.dto.response.SkippedPublish
import com.example.shiftapp.dto.response.SkippedPublishReason
import com.example.shiftapp.repository.ShiftRepository
import com.example.shiftapp.repository.ShiftSpecifications
import org.springframework.data.domain.Pageable
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime
import java.time.YearMonth

/**
 * Service for month-level schedule operations (TODO §4).
 *
 * The "publish" flow is the missing step between an ADMIN approving
 * individual shifts and the schedule being officially "live" for STAFF.
 * Today the codebase stops at APPROVED — a STAFF member can see their own
 * APPROVED shift, but there's no notion of "the month's schedule is final".
 *
 * Design choices:
 *  - We model "publish" as a per-shift status transition (APPROVED → PUBLISHED)
 *    rather than introducing a separate `ShiftSchedule` aggregate. The single
 *    source of truth stays on the `Shift` row, so the existing list / filter
 *    / RBAC paths keep working unchanged. A future `ShiftSchedule` aggregate
 *    (for audit trail / version history) can be added on top later.
 *  - The window for "the YYYY-MM schedule" is keyed on `clockInTime`,
 *    consistent with `bulkCreate` and the ADMIN list endpoint:
 *      `[yearMonth.atDay(1).atStartOfDay(), yearMonth.plusMonths(1).atDay(1).atStartOfDay())`
 *    Exclusive upper bound prevents the next month's 00:00 shift from
 *    sneaking into this month's publish.
 *  - Publishing is schedule-wide, not per-user. A future `publishMonthFor(userId)`
 *    can live next to this one if/when we need it.
 */
@Service
class ScheduleService(
    private val shiftRepository: ShiftRepository,
) {

    /**
     * Flip every APPROVED shift in [yearMonth] to PUBLISHED.
     *
     * `atomic == true` raises on any skip so `@Transactional` rolls back;
     * the global handler maps it to 409. Hibernate's `@Version` column still
     * acts as a backstop if a concurrent edit beat us to the row.
     */
    @Transactional
    fun publishMonth(yearMonth: YearMonth, atomic: Boolean = false): PublishMonthOutcome {
        val window = monthWindow(yearMonth)
        val spec = ShiftSpecifications.allOf(
            ShiftSpecifications.hasStatus(ShiftStatus.APPROVED),
            ShiftSpecifications.clockInFrom(window.start),
            // clockInTo is inclusive; subtract a nanosecond so the next
            // month's 00:00 shift doesn't sneak in.
            ShiftSpecifications.clockInTo(window.endExclusive.minusNanos(1)),
        )
        val candidates = shiftRepository.findAll(spec, Pageable.unpaged()).content

        val toSave = mutableListOf<Shift>()
        val skipped = mutableListOf<SkippedPublish>()

        for (shift in candidates) {
            if (shift.status != ShiftStatus.APPROVED) {
                // Defensive — spec already filters, but if a concurrent tx
                // raced the status away, we'd rather skip than crash.
                skipped += SkippedPublish(shift.id, SkippedPublishReason.INVALID_STATUS_TRANSITION)
                continue
            }
            toSave += shift.publish()
        }

        if (atomic && skipped.isNotEmpty()) {
            throw IllegalStateException(
                "Atomic publish of $yearMonth failed: ${skipped.size} shift(s) would be skipped"
            )
        }

        val saved = shiftRepository.saveAll(toSave).toList()
        return PublishMonthOutcome(yearMonth = yearMonth, published = saved, skipped = skipped)
    }

    /**
     * Status histogram for [yearMonth] — drives the publish-confirmation UI.
     */
    @Transactional(readOnly = true)
    fun summarize(yearMonth: YearMonth): ScheduleSummary {
        val window = monthWindow(yearMonth)
        val spec = ShiftSpecifications.allOf(
            ShiftSpecifications.clockInFrom(window.start),
            ShiftSpecifications.clockInTo(window.endExclusive.minusNanos(1)),
        )
        val shifts = shiftRepository.findAll(spec, Pageable.unpaged()).content
        val byStatus = shifts.groupingBy { it.status }.eachCount()
        return ScheduleSummary(
            yearMonth = yearMonth,
            draft = (byStatus[ShiftStatus.DRAFT] ?: 0).toLong(),
            submitted = (byStatus[ShiftStatus.SUBMITTED] ?: 0).toLong(),
            approved = (byStatus[ShiftStatus.APPROVED] ?: 0).toLong(),
            rejected = (byStatus[ShiftStatus.REJECTED] ?: 0).toLong(),
            published = (byStatus[ShiftStatus.PUBLISHED] ?: 0).toLong(),
            total = shifts.size.toLong(),
        )
    }

    private fun monthWindow(yearMonth: YearMonth): MonthWindow =
        MonthWindow(
            start = yearMonth.atDay(1).atStartOfDay(),
            endExclusive = yearMonth.plusMonths(1).atDay(1).atStartOfDay(),
        )

    private data class MonthWindow(
        val start: LocalDateTime,
        val endExclusive: LocalDateTime,
    )
}

/**
 * Service-level outcome of a successful month publish. The controller maps
 * this to [com.example.shiftapp.dto.response.PublishMonthResponse].
 */
data class PublishMonthOutcome(
    val yearMonth: YearMonth,
    val published: List<Shift>,
    val skipped: List<SkippedPublish>,
)

/**
 * Service-level status histogram for one month. Mapped to
 * [com.example.shiftapp.dto.response.ScheduleSummaryResponse] in the controller.
 */
data class ScheduleSummary(
    val yearMonth: YearMonth,
    val draft: Long,
    val submitted: Long,
    val approved: Long,
    val rejected: Long,
    val published: Long,
    val total: Long,
)
