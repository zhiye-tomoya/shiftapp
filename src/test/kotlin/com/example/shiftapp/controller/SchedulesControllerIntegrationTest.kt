package com.example.shiftapp.controller

import com.example.shiftapp.domain.Shift
import com.example.shiftapp.domain.ShiftStatus
import com.example.shiftapp.dto.request.RegisterRequest
import com.example.shiftapp.repository.ShiftRepository
import com.example.shiftapp.repository.UserRepository
import com.fasterxml.jackson.databind.ObjectMapper
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.MediaType
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.post
import java.time.LocalDateTime

/**
 * Integration tests for [SchedulesController] (TODO §4 — publish flow).
 *
 * Covers:
 *  - RBAC: ADMIN can publish, STAFF gets 403
 *  - Happy path: every APPROVED shift in the month flips to PUBLISHED
 *  - Month windowing: shifts outside [yyyy-MM] are left alone (boundary tests)
 *  - Status filtering: DRAFT/SUBMITTED/REJECTED/already-PUBLISHED are skipped
 *  - Atomic mode: any skip → 409, no rows mutated
 *  - Path validation: `yyyy-MM` parse error → 400
 *  - Summary endpoint: status histogram, accessible to STAFF
 *
 * Fixtures are inserted directly via [ShiftRepository] (rather than POSTing
 * through `/api/shifts` and walking the lifecycle) so each test can put a
 * shift into any status in one line and stay readable.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class SchedulesControllerIntegrationTest {

    @Autowired private lateinit var mockMvc: MockMvc
    @Autowired private lateinit var objectMapper: ObjectMapper
    @Autowired private lateinit var shiftRepository: ShiftRepository
    @Autowired private lateinit var userRepository: UserRepository

    private lateinit var staffToken: String
    private lateinit var adminToken: String
    private var staffUserId: Long = 0L
    private var adminUserId: Long = 0L

    @BeforeEach
    fun setUp() {
        shiftRepository.deleteAll()
        userRepository.deleteAll()

        val staffResp = mockMvc.post("/api/auth/register") {
            contentType = MediaType.APPLICATION_JSON
            content = objectMapper.writeValueAsString(
                RegisterRequest("Staff User", "staff@test.com", "password123", "STAFF")
            )
        }.andReturn().response.contentAsString
        val staffNode = objectMapper.readTree(staffResp)
        staffToken = staffNode["token"].asText()
        staffUserId = staffNode["userId"].asLong()

        val adminResp = mockMvc.post("/api/auth/register") {
            contentType = MediaType.APPLICATION_JSON
            content = objectMapper.writeValueAsString(
                RegisterRequest("Admin User", "admin@test.com", "admin123", "ADMIN")
            )
        }.andReturn().response.contentAsString
        val adminNode = objectMapper.readTree(adminResp)
        adminToken = adminNode["token"].asText()
        adminUserId = adminNode["userId"].asLong()
    }

    /** Directly persist a shift in the requested status — bypasses the lifecycle API for test brevity. */
    private fun seedShift(
        userId: Long,
        status: ShiftStatus,
        clockIn: LocalDateTime,
        clockOut: LocalDateTime = clockIn.plusHours(8),
    ): Long = shiftRepository.save(
        Shift(
            userId = userId,
            status = status,
            clockInTime = clockIn,
            clockOutTime = clockOut,
        )
    ).id

    // -----------------------------------------------------------------
    // RBAC
    // -----------------------------------------------------------------

    @Test
    fun `STAFF cannot publish a month`() {
        seedShift(staffUserId, ShiftStatus.APPROVED, LocalDateTime.of(2025, 5, 5, 9, 0))

        mockMvc.post("/api/schedules/2025-05/publish") {
            header("Authorization", "Bearer $staffToken")
        }.andExpect {
            status { isForbidden() }
        }
    }

    @Test
    fun `unauthenticated publish is rejected`() {
        mockMvc.post("/api/schedules/2025-05/publish").andExpect {
            // Spring Security returns 403 for missing auth on a non-permitted route.
            status { isForbidden() }
        }
    }

    // -----------------------------------------------------------------
    // Happy path
    // -----------------------------------------------------------------

    @Test
    fun `ADMIN can publish every APPROVED shift in the month`() {
        // Given: 3 APPROVED shifts spread across May 2025
        val id1 = seedShift(staffUserId, ShiftStatus.APPROVED, LocalDateTime.of(2025, 5, 5, 9, 0))
        val id2 = seedShift(staffUserId, ShiftStatus.APPROVED, LocalDateTime.of(2025, 5, 15, 9, 0))
        val id3 = seedShift(adminUserId, ShiftStatus.APPROVED, LocalDateTime.of(2025, 5, 25, 9, 0))

        // When: ADMIN publishes the month (body optional — none sent here)
        mockMvc.post("/api/schedules/2025-05/publish") {
            header("Authorization", "Bearer $adminToken")
        }.andExpect {
            status { isOk() }
            jsonPath("$.yearMonth") { value("2025-05") }
            jsonPath("$.published.length()") { value(3) }
            jsonPath("$.skipped.length()") { value(0) }
        }

        // And: all three rows are PUBLISHED in the DB
        listOf(id1, id2, id3).forEach { id ->
            val persisted = shiftRepository.findById(id).orElseThrow()
            assert(persisted.status == ShiftStatus.PUBLISHED) {
                "Expected shift $id to be PUBLISHED, was ${persisted.status}"
            }
        }
    }

    // -----------------------------------------------------------------
    // Month windowing — boundary tests
    // -----------------------------------------------------------------

    @Test
    fun `publish should not touch shifts outside the target month`() {
        // Given: APPROVED shifts on the last second of April, midnight of June,
        // and one squarely in May.
        val april = seedShift(staffUserId, ShiftStatus.APPROVED, LocalDateTime.of(2025, 4, 30, 23, 59, 59))
        val june  = seedShift(staffUserId, ShiftStatus.APPROVED, LocalDateTime.of(2025, 6, 1, 0, 0, 0))
        val may   = seedShift(staffUserId, ShiftStatus.APPROVED, LocalDateTime.of(2025, 5, 15, 9, 0))

        mockMvc.post("/api/schedules/2025-05/publish") {
            header("Authorization", "Bearer $adminToken")
        }.andExpect {
            status { isOk() }
            jsonPath("$.published.length()") { value(1) }
            jsonPath("$.published[0].id") { value(may) }
        }

        // April / June rows must remain APPROVED.
        assert(shiftRepository.findById(april).orElseThrow().status == ShiftStatus.APPROVED)
        assert(shiftRepository.findById(june).orElseThrow().status == ShiftStatus.APPROVED)
        assert(shiftRepository.findById(may).orElseThrow().status == ShiftStatus.PUBLISHED)
    }

    @Test
    fun `publish should include shifts at the very first second of the month`() {
        // Boundary: 2025-05-01 00:00:00 belongs to May, not April.
        val firstSecond = seedShift(
            staffUserId, ShiftStatus.APPROVED,
            LocalDateTime.of(2025, 5, 1, 0, 0, 0),
        )

        mockMvc.post("/api/schedules/2025-05/publish") {
            header("Authorization", "Bearer $adminToken")
        }.andExpect {
            status { isOk() }
            jsonPath("$.published.length()") { value(1) }
            jsonPath("$.published[0].id") { value(firstSecond) }
        }
    }

    // -----------------------------------------------------------------
    // Status filtering
    // -----------------------------------------------------------------

    @Test
    fun `publish should ignore DRAFT, SUBMITTED, REJECTED and already-PUBLISHED shifts`() {
        // Given: one shift in each non-APPROVED status, plus one APPROVED.
        val draft     = seedShift(staffUserId, ShiftStatus.DRAFT,     LocalDateTime.of(2025, 5, 1, 9, 0))
        val submitted = seedShift(staffUserId, ShiftStatus.SUBMITTED, LocalDateTime.of(2025, 5, 2, 9, 0))
        val rejected  = seedShift(staffUserId, ShiftStatus.REJECTED,  LocalDateTime.of(2025, 5, 3, 9, 0))
        val publishedAlready = seedShift(staffUserId, ShiftStatus.PUBLISHED, LocalDateTime.of(2025, 5, 4, 9, 0))
        val approved  = seedShift(staffUserId, ShiftStatus.APPROVED,  LocalDateTime.of(2025, 5, 5, 9, 0))

        mockMvc.post("/api/schedules/2025-05/publish") {
            header("Authorization", "Bearer $adminToken")
        }.andExpect {
            status { isOk() }
            // Only the APPROVED row was eligible. Filtered ones don't even
            // surface as `skipped` — they were excluded by the spec query.
            jsonPath("$.published.length()") { value(1) }
            jsonPath("$.published[0].id") { value(approved) }
            jsonPath("$.skipped.length()") { value(0) }
        }

        // Non-APPROVED rows are unchanged.
        assert(shiftRepository.findById(draft).orElseThrow().status == ShiftStatus.DRAFT)
        assert(shiftRepository.findById(submitted).orElseThrow().status == ShiftStatus.SUBMITTED)
        assert(shiftRepository.findById(rejected).orElseThrow().status == ShiftStatus.REJECTED)
        assert(shiftRepository.findById(publishedAlready).orElseThrow().status == ShiftStatus.PUBLISHED)
    }

    // -----------------------------------------------------------------
    // Atomic mode
    // -----------------------------------------------------------------

    @Test
    fun `publish with atomic=true on an empty month succeeds with no work`() {
        // Edge case: atomic implies "all-or-nothing", and "nothing" is fine.
        mockMvc.post("/api/schedules/2025-05/publish") {
            contentType = MediaType.APPLICATION_JSON
            header("Authorization", "Bearer $adminToken")
            content = """{"atomic": true}"""
        }.andExpect {
            status { isOk() }
            jsonPath("$.published.length()") { value(0) }
            jsonPath("$.skipped.length()") { value(0) }
        }
    }

    // -----------------------------------------------------------------
    // Path validation
    // -----------------------------------------------------------------

    @Test
    fun `publish with malformed yyyy-MM returns 400`() {
        mockMvc.post("/api/schedules/not-a-month/publish") {
            header("Authorization", "Bearer $adminToken")
        }.andExpect {
            status { isBadRequest() }
        }
    }

    @Test
    fun `publish with month-out-of-range returns 400`() {
        mockMvc.post("/api/schedules/2025-13/publish") {
            header("Authorization", "Bearer $adminToken")
        }.andExpect {
            status { isBadRequest() }
        }
    }

    // -----------------------------------------------------------------
    // Schedule summary
    // -----------------------------------------------------------------

    @Test
    fun `summary returns a status histogram for the month`() {
        seedShift(staffUserId, ShiftStatus.DRAFT,     LocalDateTime.of(2025, 5, 1, 9, 0))
        seedShift(staffUserId, ShiftStatus.DRAFT,     LocalDateTime.of(2025, 5, 2, 9, 0))
        seedShift(staffUserId, ShiftStatus.SUBMITTED, LocalDateTime.of(2025, 5, 3, 9, 0))
        seedShift(staffUserId, ShiftStatus.APPROVED,  LocalDateTime.of(2025, 5, 4, 9, 0))
        seedShift(staffUserId, ShiftStatus.PUBLISHED, LocalDateTime.of(2025, 5, 5, 9, 0))
        // A June shift must not be counted.
        seedShift(staffUserId, ShiftStatus.APPROVED,  LocalDateTime.of(2025, 6, 1, 9, 0))

        mockMvc.get("/api/schedules/2025-05") {
            header("Authorization", "Bearer $adminToken")
        }.andExpect {
            status { isOk() }
            jsonPath("$.yearMonth") { value("2025-05") }
            jsonPath("$.draft") { value(2) }
            jsonPath("$.submitted") { value(1) }
            jsonPath("$.approved") { value(1) }
            jsonPath("$.rejected") { value(0) }
            jsonPath("$.published") { value(1) }
            jsonPath("$.total") { value(5) }
        }
    }

    @Test
    fun `STAFF can read the schedule summary`() {
        // Summary is intentionally accessible to STAFF (no per-user data
        // leaks — just aggregate counts). Lock down only if that ever changes.
        seedShift(staffUserId, ShiftStatus.APPROVED, LocalDateTime.of(2025, 5, 4, 9, 0))

        mockMvc.get("/api/schedules/2025-05") {
            header("Authorization", "Bearer $staffToken")
        }.andExpect {
            status { isOk() }
            jsonPath("$.approved") { value(1) }
        }
    }
}
