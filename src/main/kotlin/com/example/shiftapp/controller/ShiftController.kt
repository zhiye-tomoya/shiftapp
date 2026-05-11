package com.example.shiftapp.controller

import com.example.shiftapp.domain.Shift
import com.example.shiftapp.domain.ShiftStatus
import com.example.shiftapp.dto.mapper.toResponse
import com.example.shiftapp.dto.request.ApplyShiftTemplateRequest
import com.example.shiftapp.dto.request.BulkCreateShiftRequest
import com.example.shiftapp.dto.request.BulkSubmitShiftRequest
import com.example.shiftapp.dto.request.CreateShiftRequest
import com.example.shiftapp.dto.request.ReplaceShiftRequest
import com.example.shiftapp.dto.request.UpdateShiftRequest

import com.example.shiftapp.dto.response.BulkCreateShiftResponse
import com.example.shiftapp.dto.response.BulkSubmitShiftResponse
import com.example.shiftapp.dto.response.PageResponse
import com.example.shiftapp.dto.response.ShiftResponse
import com.example.shiftapp.security.AuthenticatedUser
import com.example.shiftapp.service.ShiftService
import com.example.shiftapp.service.ShiftTemplateService
import jakarta.validation.Valid
import org.springframework.data.domain.Pageable
import org.springframework.data.domain.Sort
import org.springframework.data.web.PageableDefault
import org.springframework.format.annotation.DateTimeFormat
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.*
import java.time.LocalDateTime


/**
 * Controller for shift operations.
 *
 * Endpoints:
 * - POST /api/shifts: Create a new shift (STAFF)
 * - POST /api/shifts/{id}/submit: Submit a shift for approval (STAFF)
 * - POST /api/shifts/{id}/approve: Approve a shift (ADMIN only)
 * - POST /api/shifts/{id}/reject: Reject a shift (ADMIN only)
 * - GET /api/shifts: List all shifts with optional filters & pagination (ADMIN only)
 * - GET /api/shifts/{id}: Get a shift by ID
 * - GET /api/shifts/user/{userId}: Get all shifts for a user
 *
 * All endpoints require authentication (valid JWT token).
 * Admin-only endpoints are marked with @PreAuthorize.
 */
@RestController
@RequestMapping("/api/shifts")
class ShiftController(
    private val shiftService: ShiftService,
    private val shiftTemplateService: ShiftTemplateService,
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
            status = ShiftStatus.DRAFT,
            clockInTime = request.clockInTime,
            clockOutTime = request.clockOutTime,
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
     * List shifts across **all** users — the ADMIN overview.
     *
     * ADMIN only — STAFF should keep using `GET /api/shifts/user/{userId}`
     * for their own shifts.
     *
     * Query params (all optional):
     *  - `status` filter on shift status (DRAFT/SUBMITTED/APPROVED/REJECTED)
     *  - `userId` filter on owner
     *  - `from`   inclusive lower bound on `clockInTime` (ISO-8601, e.g. `2025-01-15T00:00:00`)
     *  - `to`     inclusive upper bound on `clockInTime` (ISO-8601)
     *  - `page`   0-based page index (default 0)
     *  - `size`   page size (default 20)
     *  - `sort`   Spring's standard `sort=field,asc|desc` syntax.
     *             Allowed fields: `id`, `status`, `userId`, `clockInTime`, `clockOutTime`.
     *             Unknown fields are silently ignored; default is `clockInTime,desc`.
     *
     * Response: [PageResponse] of [ShiftResponse].
     */
    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    fun getAllShifts(
        @RequestParam(required = false) status: ShiftStatus?,
        @RequestParam(required = false) userId: Long?,
        @RequestParam(required = false)
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) from: LocalDateTime?,
        @RequestParam(required = false)
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) to: LocalDateTime?,
        @PageableDefault(
            size = 100,
            sort = ["clockInTime"],
            direction = Sort.Direction.DESC,
        ) pageable: Pageable,
    ): ResponseEntity<PageResponse<ShiftResponse>> {
        val page = shiftService.getAllShifts(status, userId, from, to, pageable)
        return ResponseEntity.ok(PageResponse.from(page) { it.toResponse() })
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
    fun getShiftsByUser(
        @PathVariable userId: Long,
        @RequestParam(required = false) status: ShiftStatus?,
    ): ResponseEntity<List<ShiftResponse>> {
        val shifts = shiftService.getShiftsByUserId(userId, status)
        return ResponseEntity.ok(shifts.map { it.toResponse() })
    }

    // -----------------------------------------------------------------
    // Edit / delete (TODO §2)
    // -----------------------------------------------------------------

    /**
     * Replace the editable fields of an existing shift (full update).
     *
     * Idempotent by design: every editable field is required. Only mutates
     * `clockInTime`, `clockOutTime` and `userId` — `status` belongs to the
     * lifecycle endpoints (`/submit`, `/approve`, `/reject`).
     *
     * Permissions (enforced server-side):
     *  - STAFF: only own DRAFT shifts; cannot reassign `userId`
     *  - ADMIN: any shift in any status; may reassign `userId`
     *
     * Optimistic locking: include `version` in the body to fail fast (409)
     * if someone else updated the row since you read it.
     *
     * Responses:
     *  - 200 OK with [ShiftResponse]
     *  - 400 if `clockOutTime <= clockInTime`
     *  - 403 on permission violation
     *  - 409 if shift not found / version mismatch
     */
    @PutMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    fun replaceShift(
        @PathVariable id: Long,
        @Valid @RequestBody request: ReplaceShiftRequest,
        @AuthenticationPrincipal principal: AuthenticatedUser,
    ): ResponseEntity<ShiftResponse> {
        val updated = shiftService.updateShift(
            shiftId = id,
            principal = principal,
            newClockInTime = request.clockInTime,
            newClockOutTime = request.clockOutTime,
            newUserId = request.userId,
            expectedVersion = request.version,
        )
        return ResponseEntity.ok(updated.toResponse())
    }

    /**
     * Partially update an existing shift (PATCH).
     *
     * All body fields are optional but at least one of `clockInTime`,
     * `clockOutTime`, `userId` must be present (validated by the DTO).
     *
     * Permissions and optimistic-lock semantics are identical to
     * [replaceShift] — the two endpoints share the same service-layer code,
     * differing only in DTO shape.
     */
    @PatchMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    fun updateShift(
        @PathVariable id: Long,
        @Valid @RequestBody request: UpdateShiftRequest,
        @AuthenticationPrincipal principal: AuthenticatedUser,
    ): ResponseEntity<ShiftResponse> {
        val updated = shiftService.updateShift(
            shiftId = id,
            principal = principal,
            newClockInTime = request.clockInTime,
            newClockOutTime = request.clockOutTime,
            newUserId = request.userId,
            expectedVersion = request.version,
        )
        return ResponseEntity.ok(updated.toResponse())
    }

    /**
     * Delete a shift.
     *
     * Permissions:
     *  - STAFF: only own DRAFT shifts
     *  - ADMIN: any shift in any status (force-delete)
     *
     * Responses:
     *  - 204 No Content on success
     *  - 403 on permission violation
     *  - 409 if shift not found
     */
    @DeleteMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    fun deleteShift(
        @PathVariable id: Long,
        @AuthenticationPrincipal principal: AuthenticatedUser,
    ): ResponseEntity<Void> {
        shiftService.deleteShift(id, principal)
        return ResponseEntity.noContent().build()
    }

    // -----------------------------------------------------------------
    // Bulk operations (TODO §1)
    // -----------------------------------------------------------------


    /**
     * Create many DRAFT shifts for the **caller** in one round-trip.
     *
     * The owning user is taken from the JWT principal, NOT from the body —
     * a STAFF user can never bulk-create shifts on behalf of someone else.
     * (An ADMIN doing the same operation for another user would be a future
     * `POST /api/admin/shifts/bulk` endpoint, intentionally separate.)
     *
     * Response: 201 Created with [BulkCreateShiftResponse], whose `created`
     * and `skipped` arrays describe the partial-success outcome. With
     * `atomic = true` in the request, any skip raises and the whole batch is
     * rolled back (the global exception handler maps it to 409).
     */
    @PostMapping("/bulk")
    @PreAuthorize("isAuthenticated()")
    fun bulkCreate(
        @Valid @RequestBody request: BulkCreateShiftRequest,
        @AuthenticationPrincipal principal: AuthenticatedUser,
    ): ResponseEntity<BulkCreateShiftResponse> {
        val outcome = shiftService.bulkCreate(principal.userId, request)
        return ResponseEntity.status(HttpStatus.CREATED).body(outcome.toResponse())
    }

    /**
     * Promote many DRAFT shifts to SUBMITTED in one round-trip.
     *
     * PATCH because we're partially updating many existing resources at once
     * (the single-shift counterpart `POST /api/shifts/{id}/submit` is an
     * action endpoint, which is fine for one resource but doesn't compose
     * cleanly when the unit of work is a list of ids).
     *
     * Ownership and status are validated server-side per id; the caller
     * receives back exactly which ids transitioned and which were dropped
     * (with a typed reason). With `atomic = true`, any skip raises and the
     * whole batch is rolled back.
     */
    @PatchMapping("/bulk/submit")
    @PreAuthorize("isAuthenticated()")
    fun bulkSubmit(
        @Valid @RequestBody request: BulkSubmitShiftRequest,
        @AuthenticationPrincipal principal: AuthenticatedUser,
    ): ResponseEntity<BulkSubmitShiftResponse> {
        val outcome = shiftService.bulkSubmit(principal.userId, request)
        return ResponseEntity.ok(outcome.toResponse())
    }

    /**
     * Materialise a [com.example.shiftapp.domain.ShiftTemplate] into concrete
     * DRAFT shifts across `[startDate, endDate]` for the **caller** (TODO §3).
     *
     * Sits next to `POST /api/shifts/bulk` and `PATCH /api/shifts/bulk/submit`
     * so a frontend writing "create a week of shifts" learns one base URL.
     * The shift owner is taken from the JWT principal — STAFF can never
     * apply a template on behalf of someone else (IDOR defence).
     *
     * Visibility of the referenced template is enforced server-side:
     *  - global templates → anyone may apply
     *  - personal templates → only the owner (or ADMIN)
     *
     * Reuses `POST /api/shifts/bulk`'s partial-success semantics: the
     * response is identical (`created` / `skipped`), and `atomic = true`
     * triggers a 409 + full rollback on any skip.
     *
     * Responses:
     *  - 201 Created with [BulkCreateShiftResponse]
     *  - 403 if the caller cannot see the referenced template
     *  - 409 if not found, atomic-mode skip, or any other state conflict
     */
    @PostMapping("/bulk/from-template")
    @PreAuthorize("isAuthenticated()")
    fun bulkCreateFromTemplate(
        @Valid @RequestBody request: ApplyShiftTemplateRequest,
        @AuthenticationPrincipal principal: AuthenticatedUser,
    ): ResponseEntity<BulkCreateShiftResponse> {
        val outcome = shiftTemplateService.apply(request, principal)
        return ResponseEntity.status(HttpStatus.CREATED).body(outcome.toResponse())
    }
}

