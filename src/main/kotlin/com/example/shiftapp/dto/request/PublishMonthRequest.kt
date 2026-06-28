package com.example.shiftapp.dto.request

/**
 * Optional body for `POST /api/schedules/{yyyy-MM}/publish`.
 *
 * The target month comes from the URL path (`yyyy-MM`), not the body,
 * so the request can be issued with no body at all (the framework will
 * fall back on the defaults below). A POST with `{}` works identically.
 *
 * Semantics mirror `POST /api/shifts/bulk` so the frontend has a single
 * mental model for "transactional bulk lifecycle operations":
 *
 *  - [atomic] = false (default) → partial success. Any APPROVED shift that
 *    can't be flipped (e.g. someone snuck it back to DRAFT between read
 *    and write, or the @Version race lost) is reported in the response's
 *    `skipped` list and the rest still publishes.
 *  - [atomic] = true → any skip raises and `@Transactional` rolls the
 *    whole batch back. The global exception handler maps it to 409 so
 *    the caller never sees a half-published month.
 *
 * Why no `userId` filter here? Publishing is a *schedule-wide* operation —
 * the whole point is "freeze this month for everyone, all at once". A
 * future ADMIN-only `POST /api/admin/schedules/{yyyy-MM}/publish?userId=…`
 * could narrow the scope, but it's intentionally out of this round.
 */
data class PublishMonthRequest(
    /** When `true`, ANY skip aborts the whole batch and rolls back. */
    val atomic: Boolean = false,
)
