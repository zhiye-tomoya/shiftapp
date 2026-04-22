package com.example.shiftapp.repository

import com.example.shiftapp.domain.Shift
import com.example.shiftapp.domain.ShiftStatus
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

/**
 * Spring Data JPA repository for Shift persistence.
 *
 * Extends JpaRepository to get built-in CRUD operations:
 *   - findById(id): Optional<Shift>
 *   - save(shift): Shift
 *   - findAll(): List<Shift>
 *   - deleteById(id): void
 *   - and more...
 *
 * Custom query methods are derived from method names.
 */
@Repository
interface ShiftRepository : JpaRepository<Shift, Long> {
    // Custom query methods can be added here
    // Spring Data JPA automatically implements them based on method names

    // Example: Find all shifts for a specific user
    fun findAllByUserId(userId: Long): List<Shift>

    // Example: Find all shifts with a specific status
    fun findAllByStatus(status: ShiftStatus): List<Shift>
}
