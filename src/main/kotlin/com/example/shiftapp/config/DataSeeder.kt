package com.example.shiftapp.config

import com.example.shiftapp.domain.RequestStatus
import com.example.shiftapp.domain.Role
import com.example.shiftapp.domain.Shift
import com.example.shiftapp.domain.ShiftRequest
import com.example.shiftapp.domain.ShiftStatus
import com.example.shiftapp.domain.User
import com.example.shiftapp.repository.ShiftRepository
import com.example.shiftapp.repository.ShiftRequestRepository
import com.example.shiftapp.repository.UserRepository
import org.slf4j.LoggerFactory
import org.springframework.boot.CommandLineRunner
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.context.annotation.Configuration
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

/**
 * Configuration properties for the data seeder.
 *
 * Toggle / customise via application.properties:
 *
 *   app.seed.enabled=true            # set false to disable seeding
 *   app.seed.default-password=pass1234
 */
@ConfigurationProperties(prefix = "app.seed")
data class SeedProperties(
    val enabled: Boolean = true,
    val defaultPassword: String = "pass1234",
)

/**
 * Seeds the database with a sensible set of demo data on startup.
 *
 * Behaviour:
 *  - Runs only if the `users` table is empty (idempotent: safe to leave on).
 *  - Creates 1 admin + 3 staff users.
 *  - Creates a handful of shifts in every lifecycle status (DRAFT/SUBMITTED/APPROVED/REJECTED).
 *  - Creates 1 PENDING swap request between two staff members so the
 *    shift-request endpoints have something to chew on out-of-the-box.
 *
 * Credentials (all share the same default password):
 *   admin@example.com  / pass1234   (ADMIN)
 *   alice@example.com  / pass1234   (STAFF)
 *   bob@example.com    / pass1234   (STAFF)
 *   carol@example.com  / pass1234   (STAFF)
 */
@Configuration
@org.springframework.boot.context.properties.EnableConfigurationProperties(SeedProperties::class)
class DataSeeder(
    private val userRepository: UserRepository,
    private val shiftRepository: ShiftRepository,
    private val shiftRequestRepository: ShiftRequestRepository,
    private val passwordEncoder: PasswordEncoder,
    private val seedProperties: SeedProperties,
) : CommandLineRunner {

    private val log = LoggerFactory.getLogger(javaClass)

    @Transactional
    override fun run(vararg args: String?) {
        if (!seedProperties.enabled) {
            log.info("[seed] disabled via app.seed.enabled=false – skipping")
            return
        }
        if (userRepository.count() > 0) {
            log.info("[seed] users already present (count={}) – skipping", userRepository.count())
            return
        }

        log.info("[seed] empty database detected – seeding demo data…")

        val users = seedUsers()
        val shifts = seedShifts(users)
        seedShiftRequests(shifts, users)

        log.info(
            "[seed] done. users={}, shifts={}, requests={}",
            userRepository.count(),
            shiftRepository.count(),
            shiftRequestRepository.count(),
        )
        log.info("[seed] login with admin@example.com / {} (or alice|bob|carol@example.com)", seedProperties.defaultPassword)
    }

    // ---------------------------------------------------------------------
    // Users
    // ---------------------------------------------------------------------
    private fun seedUsers(): SeededUsers {
        val pwd = passwordEncoder.encode(seedProperties.defaultPassword)

        val admin = userRepository.save(
            User(name = "Admin User", email = "admin@example.com", password = pwd, role = Role.ADMIN)
        )
        val alice = userRepository.save(
            User(name = "Alice", email = "alice@example.com", password = pwd, role = Role.STAFF)
        )
        val bob = userRepository.save(
            User(name = "Bob", email = "bob@example.com", password = pwd, role = Role.STAFF)
        )
        val carol = userRepository.save(
            User(name = "Carol", email = "carol@example.com", password = pwd, role = Role.STAFF)
        )

        return SeededUsers(admin = admin, alice = alice, bob = bob, carol = carol)
    }

    // ---------------------------------------------------------------------
    // Shifts
    // ---------------------------------------------------------------------
    private fun seedShifts(users: SeededUsers): SeededShifts {
        val today = LocalDate.now()

        // Helper: build a shift on `dayOffset` from `from` to `to`.
        fun shift(userId: Long, dayOffset: Long, from: LocalTime, to: LocalTime, status: ShiftStatus): Shift {
            val date = today.plusDays(dayOffset)
            return Shift(
                userId = userId,
                clockInTime = LocalDateTime.of(date, from),
                clockOutTime = LocalDateTime.of(date, to),
                status = status,
            )
        }

        // Alice — full lifecycle coverage
        val aliceDraft = shiftRepository.save(
            shift(users.alice.id, 1, LocalTime.of(9, 0), LocalTime.of(17, 0), ShiftStatus.DRAFT)
        )
        val aliceSubmitted = shiftRepository.save(
            shift(users.alice.id, 2, LocalTime.of(10, 0), LocalTime.of(18, 0), ShiftStatus.SUBMITTED)
        )
        val aliceApproved = shiftRepository.save(
            shift(users.alice.id, 3, LocalTime.of(9, 0), LocalTime.of(17, 0), ShiftStatus.APPROVED)
        )
        val aliceRejected = shiftRepository.save(
            shift(users.alice.id, 4, LocalTime.of(13, 0), LocalTime.of(21, 0), ShiftStatus.REJECTED)
        )

        // Bob — a couple of approved shifts (one is also used as swap target context)
        val bobApproved1 = shiftRepository.save(
            shift(users.bob.id, 3, LocalTime.of(9, 0), LocalTime.of(17, 0), ShiftStatus.APPROVED)
        )
        val bobSubmitted = shiftRepository.save(
            shift(users.bob.id, 5, LocalTime.of(11, 0), LocalTime.of(19, 0), ShiftStatus.SUBMITTED)
        )

        // Carol — drafts only
        shiftRepository.save(
            shift(users.carol.id, 6, LocalTime.of(8, 0), LocalTime.of(16, 0), ShiftStatus.DRAFT)
        )

        return SeededShifts(
            aliceDraft = aliceDraft,
            aliceSubmitted = aliceSubmitted,
            aliceApproved = aliceApproved,
            aliceRejected = aliceRejected,
            bobApproved = bobApproved1,
            bobSubmitted = bobSubmitted,
        )
    }

    // ---------------------------------------------------------------------
    // Shift requests (swap requests)
    // ---------------------------------------------------------------------
    private fun seedShiftRequests(shifts: SeededShifts, users: SeededUsers) {
        // Alice asks Bob to take her APPROVED shift
        shiftRequestRepository.save(
            ShiftRequest(
                shift = shifts.aliceApproved,
                requesterId = users.alice.id,
                targetUserId = users.bob.id,
                status = RequestStatus.PENDING,
            )
        )
    }

    // ---------------------------------------------------------------------
    // Internal carriers
    // ---------------------------------------------------------------------
    private data class SeededUsers(
        val admin: User,
        val alice: User,
        val bob: User,
        val carol: User,
    )

    private data class SeededShifts(
        val aliceDraft: Shift,
        val aliceSubmitted: Shift,
        val aliceApproved: Shift,
        val aliceRejected: Shift,
        val bobApproved: Shift,
        val bobSubmitted: Shift,
    )
}
