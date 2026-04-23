package com.example.shiftapp.controller

import com.example.shiftapp.dto.mapper.toResponse
import com.example.shiftapp.dto.request.CreateSwapRequestRequest
import com.example.shiftapp.dto.response.ShiftRequestResponse
import com.example.shiftapp.service.ShiftRequestService
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.*

/**
 * Controller for shift swap request operations.
 *
 * Endpoints:
 * - POST /api/requests: Create a swap request (STAFF)
 * - POST /api/requests/{id}/approve/target: Target user approves (STAFF)
 * - POST /api/requests/{id}/reject/target: Target user rejects (STAFF)
 * - POST /api/requests/{id}/approve/admin: Admin approves (ADMIN only)
 * - POST /api/requests/{id}/reject/admin: Admin rejects (ADMIN only)
 * - GET /api/requests/requester/{requesterId}: Get requests by requester
 * - GET /api/requests/target/{targetUserId}: Get requests by target user
 *
 * Workflow:
 * 1. Staff creates swap request → PENDING
 * 2. Target user approves → TARGET_APPROVED
 * 3. Admin approves → ADMIN_APPROVED (shift ownership transferred!)
 */
@RestController
@RequestMapping("/api/requests")
class ShiftRequestController(
    private val shiftRequestService: ShiftRequestService
) {

    /**
     * Create a new shift swap request.
     *
     * Request: { shiftId, targetUserId }
     * Response: ShiftRequestResponse (201 Created)
     *
     * TODO: Extract requesterId from JWT token instead of header.
     * For now, using X-User-Id header for simplicity.
     */
    @PostMapping
    fun createSwapRequest(
        @Valid @RequestBody request: CreateSwapRequestRequest,
        @RequestHeader("X-User-Id") requesterId: Long
    ): ResponseEntity<ShiftRequestResponse> {
        val created = shiftRequestService.createRequest(
            requesterId = requesterId,
            shiftId = request.shiftId,
            targetUserId = request.targetUserId
        )
        return ResponseEntity.status(HttpStatus.CREATED).body(created.toResponse())
    }

    /**
     * Target user approves a pending request (Step 1 of 2-step approval).
     *
     * Status changes: PENDING → TARGET_APPROVED
     * Note: Shift ownership NOT transferred yet (awaits admin approval)
     */
    @PostMapping("/{id}/approve/target")
    fun approveByTargetUser(@PathVariable id: Long): ResponseEntity<ShiftRequestResponse> {
        val approved = shiftRequestService.approveByTargetUser(id)
        return ResponseEntity.ok(approved.toResponse())
    }

    /**
     * Target user rejects a pending request.
     *
     * Status changes: PENDING → TARGET_REJECTED
     * Shift ownership remains with original requester.
     */
    @PostMapping("/{id}/reject/target")
    fun rejectByTargetUser(@PathVariable id: Long): ResponseEntity<ShiftRequestResponse> {
        val rejected = shiftRequestService.rejectByTargetUser(id)
        return ResponseEntity.ok(rejected.toResponse())
    }

    /**
     * Admin approves a target-approved request (Step 2 of 2-step approval).
     *
     * ADMIN only - requires ADMIN role in JWT token.
     * Status changes: TARGET_APPROVED → ADMIN_APPROVED
     * IMPORTANT: Shift ownership transferred to target user!
     */
    @PostMapping("/{id}/approve/admin")
    @PreAuthorize("hasRole('ADMIN')")
    fun approveByAdmin(@PathVariable id: Long): ResponseEntity<ShiftRequestResponse> {
        val approved = shiftRequestService.approveByAdmin(id)
        return ResponseEntity.ok(approved.toResponse())
    }

    /**
     * Admin rejects a target-approved request.
     *
     * ADMIN only - requires ADMIN role in JWT token.
     * Status changes: TARGET_APPROVED → ADMIN_REJECTED
     * Shift ownership remains with original requester.
     */
    @PostMapping("/{id}/reject/admin")
    @PreAuthorize("hasRole('ADMIN')")
    fun rejectByAdmin(@PathVariable id: Long): ResponseEntity<ShiftRequestResponse> {
        val rejected = shiftRequestService.rejectByAdmin(id)
        return ResponseEntity.ok(rejected.toResponse())
    }

    /**
     * Get all swap requests created by a specific requester.
     *
     * Returns: List of ShiftRequestResponse (can be empty)
     */
    @GetMapping("/requester/{requesterId}")
    fun getRequestsByRequester(@PathVariable requesterId: Long): ResponseEntity<List<ShiftRequestResponse>> {
        val requests = shiftRequestService.getRequestsByRequester(requesterId)
        return ResponseEntity.ok(requests.map { it.toResponse() })
    }

    /**
     * Get all swap requests targeted at a specific user.
     *
     * Returns: List of ShiftRequestResponse (can be empty)
     * Useful for showing a user "requests waiting for your approval"
     */
    @GetMapping("/target/{targetUserId}")
    fun getRequestsByTargetUser(@PathVariable targetUserId: Long): ResponseEntity<List<ShiftRequestResponse>> {
        val requests = shiftRequestService.getRequestsByTargetUser(targetUserId)
        return ResponseEntity.ok(requests.map { it.toResponse() })
    }
}
