package com.example.shiftapp.controller

import com.example.shiftapp.dto.request.ApplyShiftTemplateRequest
import com.example.shiftapp.dto.request.CreateShiftTemplateRequest
import com.example.shiftapp.dto.request.RegisterRequest
import com.example.shiftapp.dto.request.UpdateShiftTemplateRequest
import com.example.shiftapp.repository.ShiftRepository
import com.example.shiftapp.repository.ShiftTemplateRepository
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
import org.springframework.test.web.servlet.delete
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.patch
import org.springframework.test.web.servlet.post
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime

/**
 * Integration tests for [ShiftTemplateController] + the
 * `POST /api/shifts/bulk/from-template` adapter on [ShiftController].
 *
 * Covers:
 *  - CRUD permissions (own / global / cross-user)
 *  - STAFF cannot create or hijack global templates
 *  - PATCH validation: merged time window must stay valid
 *  - Optimistic locking on update
 *  - Apply-from-template hits the bulk-create path correctly
 *  - Apply respects visibility (STAFF cannot apply someone else's personal template)
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ShiftTemplateControllerIntegrationTest {

    @Autowired private lateinit var mockMvc: MockMvc
    @Autowired private lateinit var objectMapper: ObjectMapper
    @Autowired private lateinit var userRepository: UserRepository
    @Autowired private lateinit var shiftRepository: ShiftRepository
    @Autowired private lateinit var shiftTemplateRepository: ShiftTemplateRepository

    private lateinit var staffToken: String
    private lateinit var adminToken: String
    private lateinit var otherStaffToken: String
    private var staffUserId: Long = 0
    private var adminUserId: Long = 0
    private var otherStaffUserId: Long = 0

    @BeforeEach
    fun setUp() {
        // Order matters: shifts FK users, templates have ownerId → both must be wiped first.
        shiftRepository.deleteAll()
        shiftTemplateRepository.deleteAll()
        userRepository.deleteAll()

        val staffNode = register("Staff One", "staff1@test.com", "password123", "STAFF")
        staffToken = staffNode["token"].asText()
        staffUserId = staffNode["userId"].asLong()

        val adminNode = register("Admin User", "admin@test.com", "admin1234", "ADMIN")
        adminToken = adminNode["token"].asText()
        adminUserId = adminNode["userId"].asLong()

        val otherNode = register("Staff Two", "staff2@test.com", "password123", "STAFF")
        otherStaffToken = otherNode["token"].asText()
        otherStaffUserId = otherNode["userId"].asLong()
    }

    private fun register(name: String, email: String, pwd: String, role: String) =
        objectMapper.readTree(
            mockMvc.post("/api/auth/register") {
                contentType = MediaType.APPLICATION_JSON
                content = objectMapper.writeValueAsString(RegisterRequest(name, email, pwd, role))
            }.andReturn().response.contentAsString
        )

    private fun weekdayDayShift(name: String = "Weekday day shift", shared: Boolean = false) =
        CreateShiftTemplateRequest(
            name = name,
            clockInLocalTime = LocalTime.of(9, 0),
            clockOutLocalTime = LocalTime.of(18, 0),
            daysOfWeek = setOf(
                DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY,
                DayOfWeek.THURSDAY, DayOfWeek.FRIDAY,
            ),
            shared = shared,
        )

    // -----------------------------------------------------------------
    // Create
    // -----------------------------------------------------------------

    @Test
    fun `STAFF can create a personal template owned by themselves`() {
        mockMvc.post("/api/shift-templates") {
            contentType = MediaType.APPLICATION_JSON
            header("Authorization", "Bearer $staffToken")
            content = objectMapper.writeValueAsString(weekdayDayShift())
        }.andExpect {
            status { isCreated() }
            jsonPath("$.id") { exists() }
            jsonPath("$.name") { value("Weekday day shift") }
            jsonPath("$.shared") { value(false) }
            jsonPath("$.ownerId") { value(staffUserId.toInt()) }
            jsonPath("$.daysOfWeek.length()") { value(5) }
        }
    }

    @Test
    fun `STAFF requesting shared=true is silently downgraded to personal`() {
        mockMvc.post("/api/shift-templates") {
            contentType = MediaType.APPLICATION_JSON
            header("Authorization", "Bearer $staffToken")
            content = objectMapper.writeValueAsString(weekdayDayShift(shared = true))
        }.andExpect {
            status { isCreated() }
            // Server refused to honour shared=true for STAFF — kept as personal.
            jsonPath("$.shared") { value(false) }
            jsonPath("$.ownerId") { value(staffUserId.toInt()) }
        }
    }

    @Test
    fun `ADMIN can create a shared global template`() {
        mockMvc.post("/api/shift-templates") {
            contentType = MediaType.APPLICATION_JSON
            header("Authorization", "Bearer $adminToken")
            content = objectMapper.writeValueAsString(weekdayDayShift(shared = true))
        }.andExpect {
            status { isCreated() }
            jsonPath("$.shared") { value(true) }
            jsonPath("$.ownerId") { doesNotExist() }
        }
    }

    @Test
    fun `create rejects invalid time window`() {
        val bad = weekdayDayShift().copy(
            clockInLocalTime = LocalTime.of(18, 0),
            clockOutLocalTime = LocalTime.of(9, 0),
        )
        mockMvc.post("/api/shift-templates") {
            contentType = MediaType.APPLICATION_JSON
            header("Authorization", "Bearer $staffToken")
            content = objectMapper.writeValueAsString(bad)
        }.andExpect {
            status { isBadRequest() }
        }
    }

    @Test
    fun `create rejects unauthenticated requests`() {
        mockMvc.post("/api/shift-templates") {
            contentType = MediaType.APPLICATION_JSON
            content = objectMapper.writeValueAsString(weekdayDayShift())
        }.andExpect {
            status { isForbidden() }
        }
    }

    // -----------------------------------------------------------------
    // List / Get
    // -----------------------------------------------------------------

    @Test
    fun `STAFF list returns own + global templates only`() {
        // Two personal templates owned by staff1, one global, one personal owned by staff2.
        createTemplate(staffToken, weekdayDayShift(name = "Mine A"))
        createTemplate(staffToken, weekdayDayShift(name = "Mine B"))
        createTemplate(adminToken, weekdayDayShift(name = "Shared", shared = true))
        createTemplate(otherStaffToken, weekdayDayShift(name = "Theirs"))

        val body = mockMvc.get("/api/shift-templates") {
            header("Authorization", "Bearer $staffToken")
        }.andReturn().response.contentAsString

        val names = objectMapper.readTree(body).map { it["name"].asText() }.toSet()
        assert(names == setOf("Mine A", "Mine B", "Shared")) {
            "STAFF should see own + global only, but got: $names"
        }
    }

    @Test
    fun `STAFF cannot read another STAFF's personal template`() {
        val templateId = createTemplate(otherStaffToken, weekdayDayShift(name = "Theirs"))

        mockMvc.get("/api/shift-templates/$templateId") {
            header("Authorization", "Bearer $staffToken")
        }.andExpect {
            status { isForbidden() }
        }
    }

    @Test
    fun `ADMIN can read another user's personal template`() {
        val templateId = createTemplate(staffToken, weekdayDayShift(name = "Mine"))

        mockMvc.get("/api/shift-templates/$templateId") {
            header("Authorization", "Bearer $adminToken")
        }.andExpect {
            status { isOk() }
            jsonPath("$.id") { value(templateId.toInt()) }
            jsonPath("$.ownerId") { value(staffUserId.toInt()) }
        }
    }

    // -----------------------------------------------------------------
    // Update
    // -----------------------------------------------------------------

    @Test
    fun `STAFF can update own template via PATCH`() {
        val id = createTemplate(staffToken, weekdayDayShift())

        mockMvc.patch("/api/shift-templates/$id") {
            contentType = MediaType.APPLICATION_JSON
            header("Authorization", "Bearer $staffToken")
            content = objectMapper.writeValueAsString(
                UpdateShiftTemplateRequest(name = "Renamed", roleTag = "ホール")
            )
        }.andExpect {
            status { isOk() }
            jsonPath("$.name") { value("Renamed") }
            jsonPath("$.roleTag") { value("ホール") }
            jsonPath("$.version") { value(1) }
        }
    }

    @Test
    fun `STAFF cannot update someone else's personal template`() {
        val id = createTemplate(otherStaffToken, weekdayDayShift())

        mockMvc.patch("/api/shift-templates/$id") {
            contentType = MediaType.APPLICATION_JSON
            header("Authorization", "Bearer $staffToken")
            content = objectMapper.writeValueAsString(UpdateShiftTemplateRequest(name = "Hijack"))
        }.andExpect {
            status { isForbidden() }
        }
    }

    @Test
    fun `STAFF cannot update a global template`() {
        val id = createTemplate(adminToken, weekdayDayShift(shared = true))

        mockMvc.patch("/api/shift-templates/$id") {
            contentType = MediaType.APPLICATION_JSON
            header("Authorization", "Bearer $staffToken")
            content = objectMapper.writeValueAsString(UpdateShiftTemplateRequest(name = "Hijack"))
        }.andExpect {
            status { isForbidden() }
        }
    }

    @Test
    fun `update rejects stale version with 409`() {
        val id = createTemplate(staffToken, weekdayDayShift())

        mockMvc.patch("/api/shift-templates/$id") {
            contentType = MediaType.APPLICATION_JSON
            header("Authorization", "Bearer $staffToken")
            content = objectMapper.writeValueAsString(
                UpdateShiftTemplateRequest(name = "First edit", version = 0L)
            )
        }.andExpect { status { isOk() } }

        // Re-use stale version=0L → should 409.
        mockMvc.patch("/api/shift-templates/$id") {
            contentType = MediaType.APPLICATION_JSON
            header("Authorization", "Bearer $staffToken")
            content = objectMapper.writeValueAsString(
                UpdateShiftTemplateRequest(name = "Second edit", version = 0L)
            )
        }.andExpect { status { isConflict() } }
    }

    @Test
    fun `update with only one time field still validates merged window`() {
        val id = createTemplate(staffToken, weekdayDayShift())

        // Push clockIn past clockOut without sending clockOut — merged value
        // is invalid and `ShiftTemplate.init` should bounce it as a 400.
        mockMvc.patch("/api/shift-templates/$id") {
            contentType = MediaType.APPLICATION_JSON
            header("Authorization", "Bearer $staffToken")
            content = objectMapper.writeValueAsString(
                UpdateShiftTemplateRequest(clockInLocalTime = LocalTime.of(19, 0))
            )
        }.andExpect { status { isBadRequest() } }
    }

    // -----------------------------------------------------------------
    // Delete
    // -----------------------------------------------------------------

    @Test
    fun `STAFF can delete own template`() {
        val id = createTemplate(staffToken, weekdayDayShift())

        mockMvc.delete("/api/shift-templates/$id") {
            header("Authorization", "Bearer $staffToken")
        }.andExpect { status { isNoContent() } }

        mockMvc.get("/api/shift-templates/$id") {
            header("Authorization", "Bearer $staffToken")
        }.andExpect { status { isConflict() } } // mapped from IllegalStateException
    }

    @Test
    fun `STAFF cannot delete a global template`() {
        val id = createTemplate(adminToken, weekdayDayShift(shared = true))

        mockMvc.delete("/api/shift-templates/$id") {
            header("Authorization", "Bearer $staffToken")
        }.andExpect { status { isForbidden() } }
    }

    // -----------------------------------------------------------------
    // Apply (POST /api/shifts/bulk/from-template)
    // -----------------------------------------------------------------

    @Test
    fun `apply materialises template into DRAFT shifts for the caller`() {
        val id = createTemplate(staffToken, weekdayDayShift())

        // 2025-01-06 is a Monday → Mon-Fri pattern yields 5 shifts that week.
        val response = mockMvc.post("/api/shifts/bulk/from-template") {
            contentType = MediaType.APPLICATION_JSON
            header("Authorization", "Bearer $staffToken")
            content = objectMapper.writeValueAsString(
                ApplyShiftTemplateRequest(
                    templateId = id,
                    startDate = LocalDate.of(2025, 1, 6),
                    endDate = LocalDate.of(2025, 1, 10),
                )
            )
        }.andExpect {
            status { isCreated() }
            jsonPath("$.created.length()") { value(5) }
            jsonPath("$.skipped.length()") { value(0) }
            jsonPath("$.created[0].userId") { value(staffUserId.toInt()) }
            jsonPath("$.created[0].status") { value("DRAFT") }
        }.andReturn().response.contentAsString

        // Sanity check: the persisted shifts really do belong to the caller.
        val shifts = shiftRepository.findAllByUserId(staffUserId)
        assert(shifts.size == 5) { "expected 5 shifts persisted, got ${shifts.size}" }
    }

    @Test
    fun `STAFF cannot apply another STAFF's personal template`() {
        val id = createTemplate(otherStaffToken, weekdayDayShift(name = "Theirs"))

        mockMvc.post("/api/shifts/bulk/from-template") {
            contentType = MediaType.APPLICATION_JSON
            header("Authorization", "Bearer $staffToken")
            content = objectMapper.writeValueAsString(
                ApplyShiftTemplateRequest(
                    templateId = id,
                    startDate = LocalDate.of(2025, 1, 6),
                    endDate = LocalDate.of(2025, 1, 10),
                )
            )
        }.andExpect { status { isForbidden() } }

        assert(shiftRepository.findAllByUserId(staffUserId).isEmpty()) {
            "no shifts should have been created"
        }
    }

    @Test
    fun `apply with global template works for any caller`() {
        val id = createTemplate(adminToken, weekdayDayShift(shared = true))

        mockMvc.post("/api/shifts/bulk/from-template") {
            contentType = MediaType.APPLICATION_JSON
            header("Authorization", "Bearer $staffToken")
            content = objectMapper.writeValueAsString(
                ApplyShiftTemplateRequest(
                    templateId = id,
                    startDate = LocalDate.of(2025, 1, 6),
                    endDate = LocalDate.of(2025, 1, 10),
                )
            )
        }.andExpect {
            status { isCreated() }
            jsonPath("$.created.length()") { value(5) }
        }
    }

    @Test
    fun `apply skips days that overlap existing shifts`() {
        val id = createTemplate(staffToken, weekdayDayShift())

        // First apply lays down Mon-Fri.
        mockMvc.post("/api/shifts/bulk/from-template") {
            contentType = MediaType.APPLICATION_JSON
            header("Authorization", "Bearer $staffToken")
            content = objectMapper.writeValueAsString(
                ApplyShiftTemplateRequest(
                    templateId = id,
                    startDate = LocalDate.of(2025, 1, 6),
                    endDate = LocalDate.of(2025, 1, 10),
                )
            )
        }.andExpect { status { isCreated() } }

        // Second apply over the same window should produce 0 created + 5 skipped.
        mockMvc.post("/api/shifts/bulk/from-template") {
            contentType = MediaType.APPLICATION_JSON
            header("Authorization", "Bearer $staffToken")
            content = objectMapper.writeValueAsString(
                ApplyShiftTemplateRequest(
                    templateId = id,
                    startDate = LocalDate.of(2025, 1, 6),
                    endDate = LocalDate.of(2025, 1, 10),
                )
            )
        }.andExpect {
            status { isCreated() }
            jsonPath("$.created.length()") { value(0) }
            jsonPath("$.skipped.length()") { value(5) }
            jsonPath("$.skipped[0].reason") { value("OVERLAPPING_EXISTING_SHIFT") }
        }
    }

    // -----------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------

    /** POST a template and return its persisted id. */
    private fun createTemplate(token: String, request: CreateShiftTemplateRequest): Long {
        val body = mockMvc.post("/api/shift-templates") {
            contentType = MediaType.APPLICATION_JSON
            header("Authorization", "Bearer $token")
            content = objectMapper.writeValueAsString(request)
        }.andReturn().response.contentAsString
        return objectMapper.readTree(body)["id"].asLong()
    }
}
