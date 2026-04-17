package com.example.shiftapp.repository

import com.example.shiftapp.domain.Shift

/**
 * Abstraction over Shift persistence.
 *
 * Defined as a plain interface (not Spring Data) so that:
 *   - the service layer depends only on a simple port, and
 *   - unit tests can mock it with MockK without any Spring / JPA setup.
 *
 * A real implementation (JPA, JDBC, in-memory, etc.) will be added in a
 * later phase.
 */
interface ShiftRepository {
    fun findById(id: Long): Shift?
    fun save(shift: Shift): Shift
}
