package com.example.shiftapp.controller

import com.example.shiftapp.dto.request.CreateShiftRequest
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

/**
 * Integration tests for ShiftController.
 *
 * These tests verify:
 * - Shift CRUD operations
 * - Authorization (ADMIN vs STAFF permissions)
 * - State transitions (DRAFT → SUBMITTED → APPROVED)
 * - Error handling
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ShiftControllerIntegrationTest {

    @Autowired
    private lateinit var mockMvc: MockMvc

    @Autowired
    private lateinit var objectMapper: ObjectMapper

    @Autowired
    private lateinit var shiftRepository: ShiftRepository

    @Autowired
    private lateinit var userRepository: UserRepository

    private lateinit var staffToken: String
    private lateinit var adminToken: String

    /**
     * Set up test users before each test.
     * Creates one STAFF user and one ADMIN user, stores their JWT tokens.
     */
    @BeforeEach
    fun setUp() {
        // Clean up
        shiftRepository.deleteAll()
        userRepository.deleteAll()

        // Register STAFF user
        val staffResponse = mockMvc.post("/api/auth/register") {
            contentType = MediaType.APPLICATION_JSON
            content = objectMapper.writeValueAsString(
                RegisterRequest("Staff User", "staff@test.com", "password123", "STAFF")
            )
        }.andReturn().response.contentAsString

        staffToken = objectMapper.readTree(staffResponse)["token"].asText()

        // Register ADMIN user
        val adminResponse = mockMvc.post("/api/auth/register") {
            contentType = MediaType.APPLICATION_JSON
            content = objectMapper.writeValueAsString(
                RegisterRequest("Admin User", "admin@test.com", "admin123", "ADMIN")
            )
        }.andReturn().response.contentAsString

        adminToken = objectMapper.readTree(adminResponse)["token"].asText()
    }

    @Test
    fun `should create shift when authenticated`() {
        // Given: Valid shift request
        val request = CreateShiftRequest(userId = 1L)

        // When: POST with valid token
        mockMvc.post("/api/shifts") {
            contentType = MediaType.APPLICATION_JSON
            header("Authorization", "Bearer $staffToken")
            content = objectMapper.writeValueAsString(request)
        }.andExpect {
            // Then: Returns 201 Created
            status { isCreated() }
            jsonPath("$.id") { exists() }
            jsonPath("$.userId") { value(1) }
            jsonPath("$.status") { value("DRAFT") }
        }
    }

    @Test
    fun `should reject shift creation without authentication`() {
        // Given: Valid shift request but no token
        val request = CreateShiftRequest(userId = 1L)

        // When: POST without token
        mockMvc.post("/api/shifts") {
            contentType = MediaType.APPLICATION_JSON
            content = objectMapper.writeValueAsString(request)
        }.andExpect {
            // Then: Returns 403 Forbidden (Spring Security default for missing auth)
            status { isForbidden() }
        }
    }

    @Test
    fun `should submit a draft shift`() {
        // Given: Created shift
        val createResponse = mockMvc.post("/api/shifts") {
            contentType = MediaType.APPLICATION_JSON
            header("Authorization", "Bearer $staffToken")
            content = objectMapper.writeValueAsString(CreateShiftRequest(userId = 1L))
        }.andReturn().response.contentAsString

        val shiftId = objectMapper.readTree(createResponse)["id"].asLong()

        // When: Submit the shift
        mockMvc.post("/api/shifts/$shiftId/submit") {
            header("Authorization", "Bearer $staffToken")
        }.andExpect {
            // Then: Status changes to SUBMITTED
            status { isOk() }
            jsonPath("$.id") { value(shiftId) }
            jsonPath("$.status") { value("SUBMITTED") }
        }
    }

    @Test
    fun `should allow ADMIN to approve shift`() {
        // Given: Submitted shift
        val createResponse = mockMvc.post("/api/shifts") {
            contentType = MediaType.APPLICATION_JSON
            header("Authorization", "Bearer $staffToken")
            content = objectMapper.writeValueAsString(CreateShiftRequest(userId = 1L))
        }.andReturn().response.contentAsString

        val shiftId = objectMapper.readTree(createResponse)["id"].asLong()

        mockMvc.post("/api/shifts/$shiftId/submit") {
            header("Authorization", "Bearer $staffToken")
        }

        // When: Admin approves
        mockMvc.post("/api/shifts/$shiftId/approve") {
            header("Authorization", "Bearer $adminToken")
        }.andExpect {
            // Then: Status changes to APPROVED
            status { isOk() }
            jsonPath("$.status") { value("APPROVED") }
        }
    }

    @Test
    fun `should reject STAFF user trying to approve shift`() {
        // Given: Submitted shift
        val createResponse = mockMvc.post("/api/shifts") {
            contentType = MediaType.APPLICATION_JSON
            header("Authorization", "Bearer $staffToken")
            content = objectMapper.writeValueAsString(CreateShiftRequest(userId = 1L))
        }.andReturn().response.contentAsString

        val shiftId = objectMapper.readTree(createResponse)["id"].asLong()

        mockMvc.post("/api/shifts/$shiftId/submit") {
            header("Authorization", "Bearer $staffToken")
        }

        // When: STAFF user tries to approve (should fail!)
        mockMvc.post("/api/shifts/$shiftId/approve") {
            header("Authorization", "Bearer $staffToken")
        }.andExpect {
            // Then: Access denied (Spring Security wraps in 500 with exception handler)
            status { is5xxServerError() }
        }
    }

    @Test
    fun `should allow ADMIN to reject shift`() {
        // Given: Submitted shift
        val createResponse = mockMvc.post("/api/shifts") {
            contentType = MediaType.APPLICATION_JSON
            header("Authorization", "Bearer $staffToken")
            content = objectMapper.writeValueAsString(CreateShiftRequest(userId = 1L))
        }.andReturn().response.contentAsString

        val shiftId = objectMapper.readTree(createResponse)["id"].asLong()

        mockMvc.post("/api/shifts/$shiftId/submit") {
            header("Authorization", "Bearer $staffToken")
        }

        // When: Admin rejects
        mockMvc.post("/api/shifts/$shiftId/reject") {
            header("Authorization", "Bearer $adminToken")
        }.andExpect {
            // Then: Status changes to REJECTED
            status { isOk() }
            jsonPath("$.status") { value("REJECTED") }
        }
    }

    @Test
    fun `should get shift by id`() {
        // Given: Created shift
        val createResponse = mockMvc.post("/api/shifts") {
            contentType = MediaType.APPLICATION_JSON
            header("Authorization", "Bearer $staffToken")
            content = objectMapper.writeValueAsString(CreateShiftRequest(userId = 1L))
        }.andReturn().response.contentAsString

        val shiftId = objectMapper.readTree(createResponse)["id"].asLong()

        // When: GET shift by ID
        mockMvc.get("/api/shifts/$shiftId") {
            header("Authorization", "Bearer $staffToken")
        }.andExpect {
            // Then: Returns shift data
            status { isOk() }
            jsonPath("$.id") { value(shiftId) }
            jsonPath("$.userId") { value(1) }
            jsonPath("$.status") { value("DRAFT") }
        }
    }

    @Test
    fun `should get all shifts for a user`() {
        // Given: Multiple shifts for user 1
        mockMvc.post("/api/shifts") {
            contentType = MediaType.APPLICATION_JSON
            header("Authorization", "Bearer $staffToken")
            content = objectMapper.writeValueAsString(CreateShiftRequest(userId = 1L))
        }

        mockMvc.post("/api/shifts") {
            contentType = MediaType.APPLICATION_JSON
            header("Authorization", "Bearer $staffToken")
            content = objectMapper.writeValueAsString(CreateShiftRequest(userId = 1L))
        }

        // When: GET shifts by user
        mockMvc.get("/api/shifts/user/1") {
            header("Authorization", "Bearer $staffToken")
        }.andExpect {
            // Then: Returns list of shifts
            status { isOk() }
            jsonPath("$.length()") { value(2) }
            jsonPath("$[0].userId") { value(1) }
            jsonPath("$[1].userId") { value(1) }
        }
    }

    @Test
    fun `should reject invalid state transition`() {
        // Given: DRAFT shift (never submitted)
        val createResponse = mockMvc.post("/api/shifts") {
            contentType = MediaType.APPLICATION_JSON
            header("Authorization", "Bearer $staffToken")
            content = objectMapper.writeValueAsString(CreateShiftRequest(userId = 1L))
        }.andReturn().response.contentAsString

        val shiftId = objectMapper.readTree(createResponse)["id"].asLong()

        // When: Try to approve without submitting first
        mockMvc.post("/api/shifts/$shiftId/approve") {
            header("Authorization", "Bearer $adminToken")
        }.andExpect {
            // Then: Returns 409 Conflict with error message
            status { isConflict() }
            jsonPath("$.message") { value("Only SUBMITTED shifts can be approved (was DRAFT)") }
        }
    }
}
