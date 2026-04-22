package com.example.shiftapp.repository

import com.example.shiftapp.domain.ShiftRequest
import com.example.shiftapp.domain.RequestStatus
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

/**
 * Spring Data JPA repository for ShiftRequest persistence.
 *
 * Extends JpaRepository to get built-in CRUD operations.
 * Custom query methods are derived from method names following Spring Data conventions.
 */
@Repository
interface ShiftRequestRepository : JpaRepository<ShiftRequest, Long> {
    /**
     * Find all requests created by a specific requester.
     */
    fun findAllByRequesterId(requesterId: Long): List<ShiftRequest>

    /**
     * Find all requests targeted at a specific user.
     */
    fun findAllByTargetUserId(targetUserId: Long): List<ShiftRequest>

    /**
     * Find all requests with a specific status.
     */
    fun findAllByStatus(status: RequestStatus): List<ShiftRequest>
}
