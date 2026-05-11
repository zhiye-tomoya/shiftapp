package com.example.shiftapp.domain

import jakarta.persistence.*
import java.time.DayOfWeek
import java.time.LocalTime

/**
 * Reusable shift "preset" — e.g. **平日 9-18** or **土日 10-22**.
 *
 * A template describes _the shape_ of a shift (time window + day-of-week
 * pattern), with no calendar anchor. The bulk-create flow combines it with
 * a `startDate`/`endDate` range to materialise concrete [Shift] rows.
 *
 * Ownership model:
 *  - [ownerId] = `null`  → **global** template, visible & applicable to everyone.
 *                          Only ADMIN users may create / edit / delete these.
 *  - [ownerId] = `<id>`  → **personal** template owned by that user. Only the
 *                          owner (or any ADMIN) may edit / delete it. Other
 *                          STAFF users cannot see it — keeps personal presets
 *                          like "my Wed-only late shift" private.
 *
 * Invariants:
 *  - `clockOutLocalTime` must be strictly after `clockInLocalTime`
 *    (overnight templates are out of scope until [Shift] supports them too)
 *  - [daysOfWeek] must be non-empty — an empty pattern would produce zero
 *    shifts on apply, which is almost always a bug at the call site
 *
 * Concurrency:
 *  - [version] is a JPA `@Version` field for optimistic locking, mirroring
 *    [Shift]. Edits go through [com.example.shiftapp.service.ShiftTemplateService]
 *    which does an early version check so concurrent editors get a 409 rather
 *    than a flush-time surprise.
 */
@Entity
@Table(name = "shift_templates")
data class ShiftTemplate(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0L,

    @Column(nullable = false)
    val name: String,

    @Column(name = "clock_in_local_time", nullable = false)
    val clockInLocalTime: LocalTime,

    @Column(name = "clock_out_local_time", nullable = false)
    val clockOutLocalTime: LocalTime,

    /**
     * Days of the week this template covers. Eagerly fetched so the response
     * mapper can read the set without dragging a separate transaction along —
     * templates are small and rarely change, so the eager hit is negligible.
     */
    @ElementCollection(targetClass = DayOfWeek::class, fetch = FetchType.EAGER)
    @CollectionTable(
        name = "shift_template_days",
        joinColumns = [JoinColumn(name = "template_id")],
    )
    @Enumerated(EnumType.STRING)
    @Column(name = "day_of_week", nullable = false)
    val daysOfWeek: Set<DayOfWeek>,

    /**
     * Optional free-form tag (e.g. "レジ", "ホール"). Kept as a `String?` so we
     * don't lock the product into a fixed taxonomy yet — when position/role
     * tags become a first-class feature (TODO §15) we can migrate this to a
     * proper FK.
     */
    @Column(name = "role_tag")
    val roleTag: String? = null,

    /**
     * Owner of this template. `null` means "global / shared template" — see
     * the kdoc on the class. Personal templates set this to the creator's id.
     */
    @Column(name = "owner_id")
    val ownerId: Long? = null,

    @Version
    @Column(nullable = false)
    val version: Long = 0L,
) {

    init {
        require(clockOutLocalTime.isAfter(clockInLocalTime)) {
            "clockOutLocalTime must be after clockInLocalTime " +
                    "(was $clockInLocalTime - $clockOutLocalTime)"
        }
        require(daysOfWeek.isNotEmpty()) {
            "daysOfWeek must not be empty"
        }
        require(name.isNotBlank()) {
            "name must not be blank"
        }
    }

    /**
     * `true` if this template is shared with everyone (no specific owner).
     * Used by the service layer to gate edit/delete to ADMIN.
     */
    fun isGlobal(): Boolean = ownerId == null
}
