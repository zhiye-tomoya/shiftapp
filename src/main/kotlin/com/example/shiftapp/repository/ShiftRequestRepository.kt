package com.example.shiftapp.repository

import com.example.shiftapp.domain.ShiftRequest
import com.example.shiftapp.domain.RequestStatus

/**
 * Abstraction over ShiftRequest persistence.
 *
 * Defined as a plain interface (not Spring Data) so that:
 *   - the service layer depends only on a simple port, and
 *   - unit tests can mock it with MockK without any Spring / JPA setup.
 *
 * A real implementation (JPA, JDBC, in-memory, etc.) will be added in a
 * later phase.
 */
interface ShiftRequestRepository {
    fun findById(id: Long): ShiftRequest?
    fun save(shiftRequest: ShiftRequest): ShiftRequest
    fun findAllByRequesterId(requesterId: Long): List<ShiftRequest> 
    fun findAllByTargetUserId(targetUserId: Long): List<ShiftRequest>
    fun findAllByStatus(status: RequestStatus): List<ShiftRequest> 
}
