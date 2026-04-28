package com.example.shiftapp.repository

import com.example.shiftapp.domain.Shift
import com.example.shiftapp.domain.ShiftStatus
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.JpaSpecificationExecutor
import org.springframework.stereotype.Repository

/**
 * Spring Data JPA repository for Shift persistence.
 *
 * Extends JpaRepository to get built-in CRUD operations:
 *   - findById(id): Optional<Shift>
 *   - save(shift): Shift
 *   - findAll(): List<Shift>
 *   - findAll(pageable): Page<Shift>
 *   - deleteById(id): void
 *   - and more...
 *
 * Also extends [JpaSpecificationExecutor] so we can compose dynamic
 * predicates (status / userId / date range) for the ADMIN list endpoint
 * without exploding into N derived query methods.
 *
 * Custom query methods are derived from method names.
 */
@Repository
interface ShiftRepository : JpaRepository<Shift, Long>, JpaSpecificationExecutor<Shift> {
    // ---- non-paged (used by per-user / per-status read paths) ----
    fun findAllByUserId(userId: Long): List<Shift>
    fun findAllByStatus(status: ShiftStatus): List<Shift>
}

