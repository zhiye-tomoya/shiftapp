package com.example.shiftapp.controller

import com.example.shiftapp.domain.Shift
import com.example.shiftapp.domain.ShiftStatus
import com.example.shiftapp.dto.mapper.toResponse
import com.example.shiftapp.dto.request.CreateShiftRequest
import com.example.shiftapp.dto.response.ShiftResponse
import com.example.shiftapp.service.ShiftService
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.*

/**
 * Controller for shift operations.
 *
 * Endpoints:
 * - POST /api/shifts: Create a new shift (STAFF)
 * - POST /api/shifts/{id}/submit: Submit a shift for approval (STAFF)
 * - POST /api/shifts/{id}/approve: Approve a shift (ADMIN only)
 * - POST /api/shifts/{id}/reject: Reject a shift (ADMIN only)
 * - GET /api/shifts/{id}: Get a shift by ID
 * - GET /api/shifts/user/{userId}: Get all shifts for a user
 *
 * All endpoints require authentication (valid JWT token).
 * Admin-only endpoints are marked with @PreAuthorize.
 */
@RestController
@RequestMapping("/api/shifts")
class ShiftController(
    private val shiftService: ShiftService
) {

    /**
     * Create a new shift in DRAFT status.
     *
     * Request: { userId: Long }
     * Response: ShiftResponse (201 Created)
     *
     * Note: In a real application, we'd extract userId from the JWT token
     * instead of accepting it in the request body.
     */
    @PostMapping
    fun createShift(@Valid @RequestBody request: CreateShiftRequest): ResponseEntity<ShiftResponse> {
        val shift = Shift(
            userId = request.userId,
            status = ShiftStatus.DRAFT
        )
        val created = shiftService.createShift(shift)
        return ResponseEntity.status(HttpStatus.CREATED).body(created.toResponse())
    }

    /**
     * Submit a DRAFT shift for approval.
     *
     * Status: 200 OK
     * Throws: 409 Conflict if shift is not in DRAFT status
     */
    @PostMapping("/{id}/submit")
    fun submitShift(@PathVariable id: Long): ResponseEntity<ShiftResponse> {
        val submitted = shiftService.submitShift(id)
        return ResponseEntity.ok(submitted.toResponse())
    }

    /**
     * Approve a SUBMITTED shift.
     *
     * ADMIN only - requires ADMIN role in JWT token.
     * Status: 200 OK
     * Throws: 409 Conflict if shift is not in SUBMITTED status
     */
    @PostMapping("/{id}/approve")
    @PreAuthorize("hasRole('ADMIN')")
    fun approveShift(@PathVariable id: Long): ResponseEntity<ShiftResponse> {
        val approved = shiftService.approveShift(id)
        return ResponseEntity.ok(approved.toResponse())
    }

    /**
     * Reject a SUBMITTED shift.
     *
     * ADMIN only - requires ADMIN role in JWT token.
     * Status: 200 OK
     * Throws: 409 Conflict if shift is not in SUBMITTED status
     */
    @PostMapping("/{id}/reject")
    @PreAuthorize("hasRole('ADMIN')")
    fun rejectShift(@PathVariable id: Long): ResponseEntity<ShiftResponse> {
        val rejected = shiftService.rejectShift(id)
        return ResponseEntity.ok(rejected.toResponse())
    }

    /**
     * Get a shift by ID.
     *
     * Status: 200 OK
     * Throws: 404 Not Found if shift doesn't exist
     */
    @GetMapping("/{id}")
    fun getShift(@PathVariable id: Long): ResponseEntity<ShiftResponse> {
        val shift = shiftService.getShiftById(id)
        return ResponseEntity.ok(shift.toResponse())
    }

    /**
     * Get all shifts for a specific user.
     *
     * Status: 200 OK
     * Returns: List of shifts (can be empty)
     */
    @GetMapping("/user/{userId}")
    fun getShiftsByUser(@PathVariable userId: Long): ResponseEntity<List<ShiftResponse>> {
        val shifts = shiftService.getShiftsByUserId(userId)
        return ResponseEntity.ok(shifts.map { it.toResponse() })
    }
}
