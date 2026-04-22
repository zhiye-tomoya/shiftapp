package com.example.shiftapp.domain

import jakarta.persistence.*

/**
 * ShiftRequest aggregate with rich domain model behavior.
 *
 * Represents a request to swap a shift between two users.
 * Business logic for approving/rejecting requests and transferring shift ownership
 * is encapsulated within this domain model.
 *
 * JPA annotations added for persistence, but domain logic remains unchanged.
 */
@Entity
@Table(name = "shift_requests")
data class ShiftRequest(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0L,

    @ManyToOne(fetch = FetchType.EAGER, cascade = [CascadeType.ALL])
    @JoinColumn(name = "shift_id", nullable = false)
    val shift: Shift,

    @Column(name = "requester_id", nullable = false)
    val requesterId: Long,

    @Column(name = "target_user_id", nullable = false)
    val targetUserId: Long,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    val status: RequestStatus,
) {
    init {
        require(shift.status == ShiftStatus.APPROVED) {
            "Can only swap APPROVED shifts (was ${shift.status})"
        }
        // For PENDING requests, requester must own the shift
        // For other statuses (TARGET_APPROVED, etc.), ownership may have already been transferred
        if (status == RequestStatus.PENDING) {
            require(shift.userId == requesterId) {
                "Requester must be the shift owner (shift.userId=${shift.userId}, requesterId=$requesterId)"
            }
        }
    }

    /**
     * Approve this request by the target user (first step in 2-step approval).
     *
     * Business rules:
     * - Only PENDING requests can be approved by target user
     * - Shift ownership is NOT transferred yet (awaits admin approval)
     * - Request moves to TARGET_APPROVED status (awaiting admin approval)
     *
     * @return A new ShiftRequest instance with TARGET_APPROVED status (ownership unchanged)
     * @throws IllegalStateException if the request is not in PENDING status
     */
    fun approveByTargetUser(): ShiftRequest {
        check(status == RequestStatus.PENDING) {
            "Only PENDING requests can be approved (was $status)"
        }
        
        // Ownership remains with requester until admin approval
        return copy(status = RequestStatus.TARGET_APPROVED)
    }

    /**
     * Reject this request by the target user.
     *
     * Business rules:
     * - Only PENDING requests can be rejected by target user
     * - Upon rejection, shift ownership remains with the requester
     *
     * @return A new ShiftRequest instance with TARGET_REJECTED status
     * @throws IllegalStateException if the request is not in PENDING status
     */
    fun rejectByTargetUser(): ShiftRequest {
        check(status == RequestStatus.PENDING) {
            "Only PENDING requests can be rejected (was $status)"
        }
        
        return copy(status = RequestStatus.TARGET_REJECTED)
    }

    /**
     * Approve this request by an admin (second step in 2-step approval).
     *
     * Business rules:
     * - Only TARGET_APPROVED requests can be admin-approved
     * - This is the final approval step in the 2-step approval process
     * - Shift ownership is transferred to the target user upon admin approval
     * - Request moves to ADMIN_APPROVED status (final state)
     *
     * @return A new ShiftRequest instance with ADMIN_APPROVED status and transferred ownership
     * @throws IllegalStateException if the request is not in TARGET_APPROVED status
     */
    fun approveByAdmin(): ShiftRequest {
        check(status == RequestStatus.TARGET_APPROVED) {
            "Only TARGET_APPROVED requests can be admin-approved (was $status)"
        }
        
        // Transfer shift ownership to target user (final approval)
        val transferredShift = shift.copy(userId = targetUserId)
        
        return copy(
            status = RequestStatus.ADMIN_APPROVED,
            shift = transferredShift
        )
    }

    /**
     * Reject this request by an admin.
     *
     * Business rules:
     * - Only TARGET_APPROVED requests can be admin-rejected
     * - Admin can reject even after target user approved
     * - Shift ownership remains with requester (was never transferred)
     * - This prevents the shift swap from being finalized
     *
     * @return A new ShiftRequest instance with ADMIN_REJECTED status
     * @throws IllegalStateException if the request is not in TARGET_APPROVED status
     */
    fun rejectByAdmin(): ShiftRequest {
        check(status == RequestStatus.TARGET_APPROVED) {
            "Only TARGET_APPROVED requests can be admin-rejected (was $status)"
        }
        
        // No ownership change needed - ownership was never transferred
        return copy(status = RequestStatus.ADMIN_REJECTED)
    }
}
