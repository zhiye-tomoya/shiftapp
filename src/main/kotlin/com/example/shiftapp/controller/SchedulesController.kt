package com.example.shiftapp.controller

import com.example.shiftapp.dto.mapper.toResponse
import com.example.shiftapp.dto.request.PublishMonthRequest
import com.example.shiftapp.dto.response.PublishMonthResponse
import com.example.shiftapp.dto.response.ScheduleSummaryResponse
import com.example.shiftapp.service.ScheduleService
import jakarta.validation.Valid
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.time.YearMonth
import java.time.format.DateTimeParseException

/**
 * Controller for **monthly schedule** operations (TODO §4).
 *
 * Endpoints:
 *  - `POST /api/schedules/{yyyy-MM}/publish`  → ADMIN only. Flips every
 *    APPROVED shift in the month to PUBLISHED. Body is optional; pass
 *    `{ "atomic": true }` to roll back on any skip.
 *  - `GET  /api/schedules/{yyyy-MM}`          → status histogram for the
 *    month (counts by status). Drives the publish-confirmation UI.
 *
 * Why a separate controller (not on `ShiftController`)?
 *  - The unit of work is "the month", not "a shift" — the URL space should
 *    reflect that so future per-month operations (e.g. `unpublish`,
 *    `export.csv`, `lock`) have an obvious home.
 *  - Keeps `ShiftController`'s growing surface area focused on per-shift
 *    and per-shift-batch verbs.
 */
@RestController
@RequestMapping("/api/schedules")
class SchedulesController(
    private val scheduleService: ScheduleService,
) {

    /**
     * Publish every APPROVED shift in [yearMonth] (`yyyy-MM` on the path).
     *
     * ADMIN only — publishing is an org-wide event, so it lives behind the
     * same `hasRole('ADMIN')` gate as `approve` / `reject`.
     *
     * Partial-success semantics mirror `POST /api/shifts/bulk`:
     *  - default (`atomic = false`): each unflippable shift is reported in
     *    `skipped` with a typed reason; the rest still publish.
     *  - `atomic = true`: any skip raises and `@Transactional` rolls the
     *    whole batch back — the global handler maps it to 409 Conflict so
     *    the caller never sees a half-published month.
     *
     * Responses:
     *  - 200 OK with [PublishMonthResponse]
     *  - 400 if the path isn't a valid `yyyy-MM`
     *  - 403 for non-ADMIN callers
     *  - 409 on atomic skip or optimistic-lock conflict
     */
    @PostMapping("/{yearMonth}/publish")
    @PreAuthorize("hasRole('ADMIN')")
    fun publishMonth(
        @PathVariable yearMonth: String,
        @Valid @RequestBody(required = false) request: PublishMonthRequest?,
    ): ResponseEntity<PublishMonthResponse> {
        val parsed = parseYearMonth(yearMonth)
        val effective = request ?: PublishMonthRequest()
        val outcome = scheduleService.publishMonth(parsed, atomic = effective.atomic)
        return ResponseEntity.ok(outcome.toResponse())
    }

    /**
     * Status histogram for [yearMonth].
     *
     * Authenticated only — STAFF can call it too because the body carries
     * no per-user identifying data, just aggregate counts. ADMINs use it to
     * gate the publish button; STAFF could use it for a "12 shifts approved
     * this month" badge. If we ever leak per-user data through here we'll
     * tighten the annotation to `hasRole('ADMIN')`.
     *
     * Responses:
     *  - 200 OK with [ScheduleSummaryResponse]
     *  - 400 if the path isn't a valid `yyyy-MM`
     */
    @GetMapping("/{yearMonth}")
    @PreAuthorize("isAuthenticated()")
    fun getMonthSummary(
        @PathVariable yearMonth: String,
    ): ResponseEntity<ScheduleSummaryResponse> {
        val parsed = parseYearMonth(yearMonth)
        val summary = scheduleService.summarize(parsed)
        return ResponseEntity.ok(summary.toResponse())
    }

    /**
     * Parse a `yyyy-MM` path variable, converting `DateTimeParseException`
     * into an `IllegalArgumentException` (mapped to 400 by the global
     * exception handler) so callers get a clear validation error instead
     * of the framework's default ugly 500.
     */
    private fun parseYearMonth(raw: String): YearMonth =
        try {
            YearMonth.parse(raw)
        } catch (e: DateTimeParseException) {
            throw IllegalArgumentException(
                "Invalid yearMonth '$raw' — expected ISO yyyy-MM (e.g. 2025-05)"
            )
        }
}
