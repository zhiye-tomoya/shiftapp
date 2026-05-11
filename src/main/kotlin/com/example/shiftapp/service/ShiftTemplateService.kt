package com.example.shiftapp.service

import com.example.shiftapp.domain.ShiftTemplate
import com.example.shiftapp.dto.request.ApplyShiftTemplateRequest
import com.example.shiftapp.dto.request.BulkCreateShiftRequest
import com.example.shiftapp.dto.request.CreateShiftTemplateRequest
import com.example.shiftapp.dto.request.UpdateShiftTemplateRequest
import com.example.shiftapp.repository.ShiftTemplateRepository
import com.example.shiftapp.security.AuthenticatedUser
import org.springframework.dao.OptimisticLockingFailureException
import org.springframework.security.access.AccessDeniedException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * CRUD + apply for [ShiftTemplate].
 *
 * Layering:
 *  - **CRUD lives here.** Templates are their own bounded concept (presets
 *    detached from the calendar), so they get their own controller/service.
 *  - **Apply delegates to [ShiftService.bulkCreate].** "Materialise a
 *    template into a date range" is just a bulk-create with the
 *    template-derived time window + days-of-week. Reusing the existing
 *    overlap detection, atomic-rollback semantics, and partial-success
 *    response means there is exactly one code path for "many DRAFT shifts
 *    in one transaction" — bug fixes there benefit both endpoints.
 *
 * Permission summary (enforced server-side; mirrors the kdoc on
 * [ShiftTemplate]):
 *
 *  | Op            | STAFF                       | ADMIN              |
 *  |---------------|-----------------------------|--------------------|
 *  | list          | own + global                | own + global       |
 *  | get(id)       | own + global                | any                |
 *  | create        | personal only (ownerId=self)| personal or global |
 *  | update/delete | own personal only           | any                |
 *  | apply         | any visible template        | any visible        |
 */
@Service
class ShiftTemplateService(
    private val shiftTemplateRepository: ShiftTemplateRepository,
    private val shiftService: ShiftService,
) {

    // -----------------------------------------------------------------
    // Read
    // -----------------------------------------------------------------

    /**
     * Templates the caller is allowed to see: their own + every global
     * template. ADMIN doesn't get a special view here — see the table in
     * the class kdoc. If we later need an "ADMIN sees everyone's templates"
     * endpoint, that should be a separate explicit path.
     */
    fun listVisible(principal: AuthenticatedUser): List<ShiftTemplate> =
        shiftTemplateRepository.findVisibleTo(principal.userId)

    /**
     * Fetch a single template, enforcing visibility:
     *  - global templates → everyone
     *  - personal templates → owner or ADMIN
     */
    fun getById(id: Long, principal: AuthenticatedUser): ShiftTemplate {
        val template = shiftTemplateRepository.findById(id)
            .orElseThrow { IllegalStateException("Shift template not found: $id") }

        if (!isVisibleTo(template, principal)) {
            throw AccessDeniedException("You cannot access this template")
        }
        return template
    }

    private fun isVisibleTo(template: ShiftTemplate, principal: AuthenticatedUser): Boolean {
        if (template.isGlobal()) return true
        if (principal.role == "ADMIN") return true
        return template.ownerId == principal.userId
    }

    // -----------------------------------------------------------------
    // Write
    // -----------------------------------------------------------------

    /**
     * Create a new template owned by the caller (or global, for ADMIN with
     * `shared = true`).
     *
     * `shared = true` from a STAFF caller is **silently downgraded** to a
     * personal template rather than 403'd, because the only safe default
     * is "your changes still land somewhere you can find them". The
     * frontend should hide the toggle for non-admins; this is defence in
     * depth, not the primary UX.
     */
    @Transactional
    fun create(request: CreateShiftTemplateRequest, principal: AuthenticatedUser): ShiftTemplate {
        val isAdmin = principal.role == "ADMIN"
        val ownerId: Long? = if (request.shared && isAdmin) null else principal.userId

        val template = ShiftTemplate(
            name = request.name.trim(),
            clockInLocalTime = request.clockInLocalTime,
            clockOutLocalTime = request.clockOutLocalTime,
            daysOfWeek = request.daysOfWeek,
            roleTag = request.roleTag?.trim()?.takeIf { it.isNotEmpty() },
            ownerId = ownerId,
        )
        return shiftTemplateRepository.save(template)
    }

    /**
     * Partial update with optimistic-lock pre-check.
     *
     * Permissions: STAFF may only edit a template they own personally
     * (global templates are admin-managed even if they happen to have been
     * created by that staff member in some past life — once shared, only
     * ADMIN edits). ADMIN may edit anything.
     */
    @Transactional
    fun update(
        id: Long,
        request: UpdateShiftTemplateRequest,
        principal: AuthenticatedUser,
    ): ShiftTemplate {
        val template = shiftTemplateRepository.findById(id)
            .orElseThrow { IllegalStateException("Shift template not found: $id") }

        ensureCanMutate(template, principal)

        if (request.version != null && request.version != template.version) {
            throw OptimisticLockingFailureException(
                "Shift template $id has been modified by someone else " +
                        "(expected version ${request.version}, was ${template.version})"
            )
        }

        val mergedName = request.name?.trim()?.takeIf { it.isNotEmpty() } ?: template.name
        val mergedClockIn = request.clockInLocalTime ?: template.clockInLocalTime
        val mergedClockOut = request.clockOutLocalTime ?: template.clockOutLocalTime
        val mergedDays = request.daysOfWeek ?: template.daysOfWeek
        // roleTag merge: if the client sends a string (even empty), they meant
        // to update it; if they omit the key, they want to keep the existing
        // value. We can't distinguish "explicit null" from "absent" in JSON
        // without custom plumbing, so we treat null as "keep" — to clear a
        // tag, send an empty string and we'll normalise it to null.
        val mergedRoleTag: String? = if (request.roleTag != null) {
            request.roleTag.trim().takeIf { it.isNotEmpty() }
        } else {
            template.roleTag
        }

        // Quick no-op: skip the write so we don't bump @Version for free.
        if (mergedName == template.name &&
            mergedClockIn == template.clockInLocalTime &&
            mergedClockOut == template.clockOutLocalTime &&
            mergedDays == template.daysOfWeek &&
            mergedRoleTag == template.roleTag
        ) {
            return template
        }

        // `ShiftTemplate.init` re-runs the invariants on the merged values,
        // surfacing IllegalArgumentException → 400 via GlobalExceptionHandler.
        val updated = template.copy(
            name = mergedName,
            clockInLocalTime = mergedClockIn,
            clockOutLocalTime = mergedClockOut,
            daysOfWeek = mergedDays,
            roleTag = mergedRoleTag,
        )

        return shiftTemplateRepository.save(updated)
    }

    /**
     * Hard delete a template. Existing shifts that were created from it are
     * unaffected (we never persist the template id on the shift — once
     * applied, the shift is its own object).
     */
    @Transactional
    fun delete(id: Long, principal: AuthenticatedUser) {
        val template = shiftTemplateRepository.findById(id)
            .orElseThrow { IllegalStateException("Shift template not found: $id") }

        ensureCanMutate(template, principal)
        shiftTemplateRepository.delete(template)
    }

    private fun ensureCanMutate(template: ShiftTemplate, principal: AuthenticatedUser) {
        val isAdmin = principal.role == "ADMIN"
        if (isAdmin) return
        if (template.isGlobal()) {
            throw AccessDeniedException("Only ADMIN can modify a shared template")
        }
        if (template.ownerId != principal.userId) {
            throw AccessDeniedException("You can only modify templates you own")
        }
    }

    // -----------------------------------------------------------------
    // Apply (bulk-create from template)
    // -----------------------------------------------------------------

    /**
     * Materialise [request.templateId] into concrete DRAFT shifts across
     * `[startDate, endDate]`, owned by [principal].
     *
     * The heavy lifting (overlap detection, atomic rollback, partial-success
     * response) is delegated to [ShiftService.bulkCreate] so apply-from-
     * template stays a thin adapter — one fewer place to forget about IDOR
     * defences or N+1 fetches.
     */
    @Transactional
    fun apply(
        request: ApplyShiftTemplateRequest,
        principal: AuthenticatedUser,
    ): BulkCreateOutcome {
        val template = getById(request.templateId, principal)

        val bulkRequest = BulkCreateShiftRequest(
            startDate = request.startDate,
            endDate = request.endDate,
            daysOfWeek = template.daysOfWeek,
            clockInLocalTime = template.clockInLocalTime,
            clockOutLocalTime = template.clockOutLocalTime,
            skipOverlapping = request.skipOverlapping,
            atomic = request.atomic,
        )
        return shiftService.bulkCreate(principal.userId, bulkRequest)
    }
}
