package com.example.shiftapp.repository

import com.example.shiftapp.domain.Shift
import com.example.shiftapp.domain.ShiftStatus
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
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
 * Custom query methods are derived from method names.
 */
@Repository
interface ShiftRepository : JpaRepository<Shift, Long> {
    // ---- non-paged (used by per-user / per-status read paths) ----
    fun findAllByUserId(userId: Long): List<Shift>
    fun findAllByStatus(status: ShiftStatus): List<Shift>

    // ---- paged + optional filters (used by ADMIN list endpoint) ----
    fun findAllByStatus(status: ShiftStatus, pageable: Pageable): Page<Shift>
    fun findAllByUserId(userId: Long, pageable: Pageable): Page<Shift>
    fun findAllByStatusAndUserId(status: ShiftStatus, userId: Long, pageable: Pageable): Page<Shift>
}
