package com.example.shiftapp.controller

import com.example.shiftapp.dto.mapper.toResponse
import com.example.shiftapp.dto.request.CreateShiftTemplateRequest
import com.example.shiftapp.dto.request.UpdateShiftTemplateRequest
import com.example.shiftapp.dto.response.ShiftTemplateResponse
import com.example.shiftapp.security.AuthenticatedUser
import com.example.shiftapp.service.ShiftTemplateService
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.*

/**
 * CRUD endpoints for [com.example.shiftapp.domain.ShiftTemplate].
 *
 * The "apply" / materialise-into-shifts endpoint deliberately lives in
 * [ShiftController] as `POST /api/shifts/bulk/from-template` so it sits
 * next to its siblings `POST /api/shifts/bulk` and
 * `PATCH /api/shifts/bulk/submit` — the consumer doesn't have to learn a
 * second base URL to write "create a week of shifts".
 *
 * All endpoints require authentication; the service enforces the per-row
 * visibility / ownership rules (see [ShiftTemplateService] kdoc).
 */
@RestController
@RequestMapping("/api/shift-templates")
class ShiftTemplateController(
    private val shiftTemplateService: ShiftTemplateService,
) {

    /**
     * List every template visible to the caller (own + global).
     *
     * Returned in alphabetical order by name so the dropdown the frontend
     * builds from this list is stable across reloads.
     */
    @GetMapping
    @PreAuthorize("isAuthenticated()")
    fun list(
        @AuthenticationPrincipal principal: AuthenticatedUser,
    ): ResponseEntity<List<ShiftTemplateResponse>> {
        val templates = shiftTemplateService.listVisible(principal)
        return ResponseEntity.ok(templates.map { it.toResponse() })
    }

    /**
     * Get a single template by id.
     *
     * 403 if the template is personal and the caller is neither owner nor ADMIN.
     * 409 if not found (consistent with other endpoints — see GlobalExceptionHandler).
     */
    @GetMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    fun get(
        @PathVariable id: Long,
        @AuthenticationPrincipal principal: AuthenticatedUser,
    ): ResponseEntity<ShiftTemplateResponse> {
        val template = shiftTemplateService.getById(id, principal)
        return ResponseEntity.ok(template.toResponse())
    }

    /**
     * Create a new template.
     *
     * STAFF callers always get a personal template (owned by themselves);
     * ADMIN callers may set `shared = true` to create a global one. See
     * [CreateShiftTemplateRequest] for the rationale on silent downgrade.
     */
    @PostMapping
    @PreAuthorize("isAuthenticated()")
    fun create(
        @Valid @RequestBody request: CreateShiftTemplateRequest,
        @AuthenticationPrincipal principal: AuthenticatedUser,
    ): ResponseEntity<ShiftTemplateResponse> {
        val created = shiftTemplateService.create(request, principal)
        return ResponseEntity.status(HttpStatus.CREATED).body(created.toResponse())
    }

    /**
     * Partially update an existing template.
     *
     * - 400 if the merged time window is invalid (e.g. only `clockInLocalTime`
     *   sent and it ends up >= persisted `clockOutLocalTime`)
     * - 403 if the caller is not allowed to mutate this template
     * - 409 if not found or the optimistic-lock check fails
     */
    @PatchMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    fun update(
        @PathVariable id: Long,
        @Valid @RequestBody request: UpdateShiftTemplateRequest,
        @AuthenticationPrincipal principal: AuthenticatedUser,
    ): ResponseEntity<ShiftTemplateResponse> {
        val updated = shiftTemplateService.update(id, request, principal)
        return ResponseEntity.ok(updated.toResponse())
    }

    /**
     * Delete a template.
     *
     * - 204 No Content on success
     * - 403 on permission violation
     * - 409 if not found
     */
    @DeleteMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    fun delete(
        @PathVariable id: Long,
        @AuthenticationPrincipal principal: AuthenticatedUser,
    ): ResponseEntity<Void> {
        shiftTemplateService.delete(id, principal)
        return ResponseEntity.noContent().build()
    }
}
