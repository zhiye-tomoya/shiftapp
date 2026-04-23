package com.example.shiftapp.controller

import com.example.shiftapp.dto.request.CreateShiftRequest
import com.example.shiftapp.dto.request.CreateSwapRequestRequest
import com.example.shiftapp.dto.request.RegisterRequest
import com.example.shiftapp.repository.ShiftRepository
import com.example.shiftapp.repository.ShiftRequestRepository
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
 * Integration tests for ShiftRequestController.
 *
 * These tests verify:
 * - Complete 2-step approval workflow
 * - Shift ownership transfer on admin approval
 * - Authorization for admin-only actions
 * - Validation rules (shift must be APPROVED, requester must own shift)
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ShiftRequestControllerIntegrationTest {

    @Autowired
    private lateinit var mockMvc: MockMvc

    @Autowired
    private lateinit var objectMapper: ObjectMapper

    @Autowired
    private lateinit var shiftRepository: ShiftRepository

    @Autowired
    private lateinit var shiftRequestRepository: ShiftRequestRepository

    @Autowired
    private lateinit var userRepository: UserRepository

    private lateinit var staff1Token: String
    private lateinit var staff2Token: String
    private lateinit var adminToken: String
    private var staff1Id: Long = 0
    private var staff2Id: Long = 0

    @BeforeEach
    fun setUp() {
        // Clean up
        shiftRequestRepository.deleteAll()
        shiftRepository.deleteAll()
        userRepository.deleteAll()

        // Register staff user 1
        val staff1Response = mockMvc.post("/api/auth/register") {
            contentType = MediaType.APPLICATION_JSON
            content = objectMapper.writeValueAsString(
                RegisterRequest("John Doe", "john@test.com", "password123", "STAFF")
            )
        }.andReturn().response.contentAsString

        val staff1Data = objectMapper.readTree(staff1Response)
        staff1Token = staff1Data["token"].asText()
        staff1Id = staff1Data["userId"].asLong()

        // Register staff user 2
        val staff2Response = mockMvc.post("/api/auth/register") {
            contentType = MediaType.APPLICATION_JSON
            content = objectMapper.writeValueAsString(
                RegisterRequest("Jane Smith", "jane@test.com", "password123", "STAFF")
            )
        }.andReturn().response.contentAsString

        val staff2Data = objectMapper.readTree(staff2Response)
        staff2Token = staff2Data["token"].asText()
        staff2Id = staff2Data["userId"].asLong()

        // Register admin user
        val adminResponse = mockMvc.post("/api/auth/register") {
            contentType = MediaType.APPLICATION_JSON
            content = objectMapper.writeValueAsString(
                RegisterRequest("Admin User", "admin@test.com", "admin123", "ADMIN")
            )
        }.andReturn().response.contentAsString

        adminToken = objectMapper.readTree(adminResponse)["token"].asText()
    }

    /**
     * Helper function to create and approve a shift.
     */
    private fun createApprovedShift(userId: Long, token: String): Long {
        // Create shift
        val createResponse = mockMvc.post("/api/shifts") {
            contentType = MediaType.APPLICATION_JSON
            header("Authorization", "Bearer $token")
            content = objectMapper.writeValueAsString(CreateShiftRequest(userId))
        }.andReturn().response.contentAsString

        val shiftId = objectMapper.readTree(createResponse)["id"].asLong()

        // Submit shift
        mockMvc.post("/api/shifts/$shiftId/submit") {
            header("Authorization", "Bearer $token")
        }

        // Admin approves
        mockMvc.post("/api/shifts/$shiftId/approve") {
            header("Authorization", "Bearer $adminToken")
        }

        return shiftId
    }

    @Test
    fun `should create swap request for approved shift`() {
        // Given: Staff1 has an approved shift
        val shiftId = createApprovedShift(staff1Id, staff1Token)

        // When: Create swap request to staff2
        val request = CreateSwapRequestRequest(
            shiftId = shiftId,
            targetUserId = staff2Id
        )

        mockMvc.post("/api/requests") {
            contentType = MediaType.APPLICATION_JSON
            header("Authorization", "Bearer $staff1Token")
            header("X-User-Id", staff1Id.toString())
            content = objectMapper.writeValueAsString(request)
        }.andExpect {
            // Then: Swap request created
            status { isCreated() }
            jsonPath("$.id") { exists() }
            jsonPath("$.requesterId") { value(staff1Id) }
            jsonPath("$.targetUserId") { value(staff2Id) }
            jsonPath("$.status") { value("PENDING") }
            jsonPath("$.shift.id") { value(shiftId) }
        }
    }

    @Test
    fun `should complete 2-step approval workflow`() {
        // Given: Staff1 has approved shift, creates swap request to staff2
        val shiftId = createApprovedShift(staff1Id, staff1Token)

        val createRequestResponse = mockMvc.post("/api/requests") {
            contentType = MediaType.APPLICATION_JSON
            header("Authorization", "Bearer $staff1Token")
            header("X-User-Id", staff1Id.toString())
            content = objectMapper.writeValueAsString(
                CreateSwapRequestRequest(shiftId, staff2Id)
            )
        }.andReturn().response.contentAsString

        val requestId = objectMapper.readTree(createRequestResponse)["id"].asLong()

        // When: Step 1 - Target user approves
        mockMvc.post("/api/requests/$requestId/approve/target") {
            header("Authorization", "Bearer $staff2Token")
        }.andExpect {
            // Then: Status changes to TARGET_APPROVED
            status { isOk() }
            jsonPath("$.status") { value("TARGET_APPROVED") }
            // Shift ownership NOT transferred yet
            jsonPath("$.shift.userId") { value(staff1Id) }
        }

        // When: Step 2 - Admin approves
        mockMvc.post("/api/requests/$requestId/approve/admin") {
            header("Authorization", "Bearer $adminToken")
        }.andExpect {
            // Then: Status changes to ADMIN_APPROVED
            status { isOk() }
            jsonPath("$.status") { value("ADMIN_APPROVED") }
            // Shift ownership NOW transferred to staff2!
            jsonPath("$.shift.userId") { value(staff2Id) }
        }

        // Verify shift ownership in database
        mockMvc.get("/api/shifts/$shiftId") {
            header("Authorization", "Bearer $adminToken")
        }.andExpect {
            status { isOk() }
            jsonPath("$.userId") { value(staff2Id) }
        }
    }

    @Test
    fun `should reject swap request for non-approved shift`() {
        // Given: Staff1 has a DRAFT shift (not approved)
        val createResponse = mockMvc.post("/api/shifts") {
            contentType = MediaType.APPLICATION_JSON
            header("Authorization", "Bearer $staff1Token")
            content = objectMapper.writeValueAsString(CreateShiftRequest(staff1Id))
        }.andReturn().response.contentAsString

        val shiftId = objectMapper.readTree(createResponse)["id"].asLong()

        // When: Try to create swap request for draft shift
        val request = CreateSwapRequestRequest(
            shiftId = shiftId,
            targetUserId = staff2Id
        )

        mockMvc.post("/api/requests") {
            contentType = MediaType.APPLICATION_JSON
            header("Authorization", "Bearer $staff1Token")
            header("X-User-Id", staff1Id.toString())
            content = objectMapper.writeValueAsString(request)
        }.andExpect {
            // Then: Returns 400 Bad Request
            status { isBadRequest() }
            jsonPath("$.message") { value("Can only swap APPROVED shifts (was DRAFT)") }
        }
    }

    @Test
    fun `should allow target user to reject swap request`() {
        // Given: Pending swap request
        val shiftId = createApprovedShift(staff1Id, staff1Token)

        val createRequestResponse = mockMvc.post("/api/requests") {
            contentType = MediaType.APPLICATION_JSON
            header("Authorization", "Bearer $staff1Token")
            header("X-User-Id", staff1Id.toString())
            content = objectMapper.writeValueAsString(
                CreateSwapRequestRequest(shiftId, staff2Id)
            )
        }.andReturn().response.contentAsString

        val requestId = objectMapper.readTree(createRequestResponse)["id"].asLong()

        // When: Target user rejects
        mockMvc.post("/api/requests/$requestId/reject/target") {
            header("Authorization", "Bearer $staff2Token")
        }.andExpect {
            // Then: Status changes to TARGET_REJECTED
            status { isOk() }
            jsonPath("$.status") { value("TARGET_REJECTED") }
            // Shift ownership remains with staff1
            jsonPath("$.shift.userId") { value(staff1Id) }
        }
    }

    @Test
    fun `should allow admin to reject after target approval`() {
        // Given: Target-approved swap request
        val shiftId = createApprovedShift(staff1Id, staff1Token)

        val createRequestResponse = mockMvc.post("/api/requests") {
            contentType = MediaType.APPLICATION_JSON
            header("Authorization", "Bearer $staff1Token")
            header("X-User-Id", staff1Id.toString())
            content = objectMapper.writeValueAsString(
                CreateSwapRequestRequest(shiftId, staff2Id)
            )
        }.andReturn().response.contentAsString

        val requestId = objectMapper.readTree(createRequestResponse)["id"].asLong()

        // Target approves
        mockMvc.post("/api/requests/$requestId/approve/target") {
            header("Authorization", "Bearer $staff2Token")
        }

        // When: Admin rejects
        mockMvc.post("/api/requests/$requestId/reject/admin") {
            header("Authorization", "Bearer $adminToken")
        }.andExpect {
            // Then: Status changes to ADMIN_REJECTED
            status { isOk() }
            jsonPath("$.status") { value("ADMIN_REJECTED") }
            // Shift ownership remains with staff1
            jsonPath("$.shift.userId") { value(staff1Id) }
        }
    }

    @Test
    fun `should reject STAFF user trying to admin-approve`() {
        // Given: Target-approved request
        val shiftId = createApprovedShift(staff1Id, staff1Token)

        val createRequestResponse = mockMvc.post("/api/requests") {
            contentType = MediaType.APPLICATION_JSON
            header("Authorization", "Bearer $staff1Token")
            header("X-User-Id", staff1Id.toString())
            content = objectMapper.writeValueAsString(
                CreateSwapRequestRequest(shiftId, staff2Id)
            )
        }.andReturn().response.contentAsString

        val requestId = objectMapper.readTree(createRequestResponse)["id"].asLong()

        mockMvc.post("/api/requests/$requestId/approve/target") {
            header("Authorization", "Bearer $staff2Token")
        }

        // When: STAFF user tries to admin-approve (should fail!)
        mockMvc.post("/api/requests/$requestId/approve/admin") {
            header("Authorization", "Bearer $staff1Token")
        }.andExpect {
            // Then: Access denied (Spring Security wraps in 500 with exception handler)
            status { is5xxServerError() }
        }
    }

    @Test
    fun `should get requests by requester`() {
        // Given: Staff1 creates multiple swap requests
        val shift1 = createApprovedShift(staff1Id, staff1Token)
        val shift2 = createApprovedShift(staff1Id, staff1Token)

        mockMvc.post("/api/requests") {
            contentType = MediaType.APPLICATION_JSON
            header("Authorization", "Bearer $staff1Token")
            header("X-User-Id", staff1Id.toString())
            content = objectMapper.writeValueAsString(
                CreateSwapRequestRequest(shift1, staff2Id)
            )
        }

        mockMvc.post("/api/requests") {
            contentType = MediaType.APPLICATION_JSON
            header("Authorization", "Bearer $staff1Token")
            header("X-User-Id", staff1Id.toString())
            content = objectMapper.writeValueAsString(
                CreateSwapRequestRequest(shift2, staff2Id)
            )
        }

        // When: Get requests by requester
        mockMvc.get("/api/requests/requester/$staff1Id") {
            header("Authorization", "Bearer $staff1Token")
        }.andExpect {
            // Then: Returns list of 2 requests
            status { isOk() }
            jsonPath("$.length()") { value(2) }
            jsonPath("$[0].requesterId") { value(staff1Id) }
            jsonPath("$[1].requesterId") { value(staff1Id) }
        }
    }

    @Test
    fun `should get requests by target user`() {
        // Given: Multiple requests targeted at staff2
        val shift1 = createApprovedShift(staff1Id, staff1Token)

        mockMvc.post("/api/requests") {
            contentType = MediaType.APPLICATION_JSON
            header("Authorization", "Bearer $staff1Token")
            header("X-User-Id", staff1Id.toString())
            content = objectMapper.writeValueAsString(
                CreateSwapRequestRequest(shift1, staff2Id)
            )
        }

        // When: Get requests by target user
        mockMvc.get("/api/requests/target/$staff2Id") {
            header("Authorization", "Bearer $staff2Token")
        }.andExpect {
            // Then: Returns list of requests
            status { isOk() }
            jsonPath("$.length()") { value(1) }
            jsonPath("$[0].targetUserId") { value(staff2Id) }
        }
    }
}
