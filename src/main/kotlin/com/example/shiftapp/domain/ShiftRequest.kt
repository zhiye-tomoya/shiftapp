package com.example.shiftapp.domain

/**
 * ShiftRequest aggregate with rich domain model behavior.
 *
 * Represents a request to swap a shift between two users.
 * Business logic for approving/rejecting requests and transferring shift ownership
 * is encapsulated within this domain model.
 *
 * Following DDD principles:
 * - Business rules are enforced in the domain, NOT in the service layer
 * - State transitions are immutable (returns new instances)
 * - Invalid operations throw exceptions
 *
 * @property id Unique identifier for this request
 * @property shift The shift being requested for swap
 * @property requesterId User ID of the person requesting to give up the shift
 * @property targetUserId User ID of the person being asked to take the shift
 * @property status Current status of the request
 */
data class ShiftRequest(
    val id: Long,
    val shift: Shift,
    val requesterId: Long,
    val targetUserId: Long,
    val status: RequestStatus,
) {
    /**
     * Approve this request by the target user.
     *
     * Business rules:
     * - Only PENDING requests can be approved
     * - Upon approval, shift ownership is transferred to the target user
     *
     * @return A new ShiftRequest instance with APPROVED status and updated shift ownership
     * @throws IllegalStateException if the request is not in PENDING status
     */
    fun approveByTargetUser(): ShiftRequest {
        check(status == RequestStatus.PENDING) {
            "Only PENDING requests can be approved (was $status)"
        }
        
        // Transfer shift ownership to target user
        val transferredShift = shift.copy(userId = targetUserId)
        
        return copy(
            status = RequestStatus.APPROVED,
            shift = transferredShift
        )
    }

    /**
     * Reject this request by the target user.
     *
     * Business rules:
     * - Only PENDING requests can be rejected
     * - Upon rejection, shift ownership remains with the requester
     *
     * @return A new ShiftRequest instance with REJECTED status
     * @throws IllegalStateException if the request is not in PENDING status
     */
    fun rejectByTargetUser(): ShiftRequest {
        check(status == RequestStatus.PENDING) {
            "Only PENDING requests can be rejected (was $status)"
        }
        
        return copy(status = RequestStatus.REJECTED)
    }
}
