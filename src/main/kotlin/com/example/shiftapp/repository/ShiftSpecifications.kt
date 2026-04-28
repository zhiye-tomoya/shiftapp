package com.example.shiftapp.repository

import com.example.shiftapp.domain.Shift
import com.example.shiftapp.domain.ShiftStatus
import org.springframework.data.jpa.domain.Specification
import java.time.LocalDateTime

/**
 * Composable JPA [Specification] factories for [Shift].
 *
 * Each factory returns a `Specification<Shift>?` so that `null` cleanly
 * means "no predicate, skip me" — callers combine the non-null ones with
 * [Specification.allOf] (logical AND).
 *
 * Filters are deliberately scoped to what the ADMIN list endpoint exposes:
 *  - [hasStatus]     equality on `status`
 *  - [hasUserId]     equality on `userId`
 *  - [clockInFrom]   inclusive lower bound on `clockInTime`
 *  - [clockInTo]     inclusive upper bound on `clockInTime`
 *
 * Date filtering targets `clockInTime` because the typical ADMIN UI is a
 * calendar/timeline keyed on shift start. If a different anchor is ever
 * needed we can add a sibling `clockOutBetween` factory.
 */
object ShiftSpecifications {

    fun hasStatus(status: ShiftStatus?): Specification<Shift>? =
        status?.let {
            Specification { root, _, cb -> cb.equal(root.get<ShiftStatus>("status"), it) }
        }

    fun hasUserId(userId: Long?): Specification<Shift>? =
        userId?.let {
            Specification { root, _, cb -> cb.equal(root.get<Long>("userId"), it) }
        }

    fun clockInFrom(from: LocalDateTime?): Specification<Shift>? =
        from?.let {
            Specification { root, _, cb ->
                cb.greaterThanOrEqualTo(root.get("clockInTime"), it)
            }
        }

    fun clockInTo(to: LocalDateTime?): Specification<Shift>? =
        to?.let {
            Specification { root, _, cb ->
                cb.lessThanOrEqualTo(root.get("clockInTime"), it)
            }
        }

    /**
     * Combine the supplied [specs] with logical AND, ignoring any nulls.
     * Returns a "match-all" spec when every input is null so that the
     * caller can always pass the result straight into the repository.
     */
    fun allOf(vararg specs: Specification<Shift>?): Specification<Shift> =
        specs.filterNotNull()
            .fold(Specification.where<Shift>(null)) { acc, spec -> acc.and(spec) }
}
