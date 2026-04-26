package com.example.shiftapp.controller

import com.example.shiftapp.dto.request.LoginRequest
import com.example.shiftapp.dto.request.RegisterRequest
import com.example.shiftapp.repository.UserRepository
import com.fasterxml.jackson.databind.ObjectMapper
import jakarta.servlet.http.Cookie
import org.hamcrest.Matchers.containsString
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.post

/**
 * Integration tests for AuthController.
 *
 * These tests verify:
 * - User registration with validation
 * - User login with JWT token generation
 * - Error handling for invalid inputs
 *
 * @SpringBootTest: Starts full Spring context with all beans
 * @AutoConfigureMockMvc: Provides MockMvc for testing HTTP endpoints
 * @ActiveProfiles("test"): Uses test configuration (H2 in-memory database)
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AuthControllerIntegrationTest {

    @Autowired
    private lateinit var mockMvc: MockMvc

    @Autowired
    private lateinit var objectMapper: ObjectMapper

    @Autowired
    private lateinit var userRepository: UserRepository

    /**
     * Clean up database before each test.
     * Ensures tests are isolated and don't affect each other.
     */
    @BeforeEach
    fun setUp() {
        userRepository.deleteAll()
    }

    @Test
    fun `should register new user with valid data`() {
        // Given: Valid registration request
        val request = RegisterRequest(
            name = "John Doe",
            email = "john@example.com",
            password = "password123",
            role = "STAFF"
        )

        // When: POST to /api/auth/register
        mockMvc.post("/api/auth/register") {
            contentType = MediaType.APPLICATION_JSON
            content = objectMapper.writeValueAsString(request)
        }.andExpect {
            // Then: Returns 201 Created
            status { isCreated() }

            // Response contains JWT token
            jsonPath("$.token") { exists() }

            // Response contains user info
            jsonPath("$.userId") { exists() }
            jsonPath("$.email") { value("john@example.com") }
            jsonPath("$.role") { value("STAFF") }
        }
    }

    @Test
    fun `should reject registration with duplicate email`() {
        // Given: User already registered
        val request = RegisterRequest(
            name = "John Doe",
            email = "john@example.com",
            password = "password123",
            role = "STAFF"
        )

        // Register first time
        mockMvc.post("/api/auth/register") {
            contentType = MediaType.APPLICATION_JSON
            content = objectMapper.writeValueAsString(request)
        }

        // When: Try to register same email again
        mockMvc.post("/api/auth/register") {
            contentType = MediaType.APPLICATION_JSON
            content = objectMapper.writeValueAsString(request)
        }.andExpect {
            // Then: Returns 400 Bad Request
            status { isBadRequest() }
            jsonPath("$.message") { value("Email already exists") }
        }
    }

    @Test
    fun `should reject registration with invalid role`() {
        // Given: Invalid role
        val request = RegisterRequest(
            name = "John Doe",
            email = "john@example.com",
            password = "password123",
            role = "INVALID_ROLE"
        )

        // When: POST with invalid role
        mockMvc.post("/api/auth/register") {
            contentType = MediaType.APPLICATION_JSON
            content = objectMapper.writeValueAsString(request)
        }.andExpect {
            // Then: Returns 400 Bad Request
            status { isBadRequest() }
            jsonPath("$.message") { value("Invalid role: INVALID_ROLE. Must be ADMIN or STAFF") }
        }
    }

    @Test
    fun `should reject registration with short password`() {
        // Given: Password too short (< 8 characters)
        val request = RegisterRequest(
            name = "John Doe",
            email = "john@example.com",
            password = "short",
            role = "STAFF"
        )

        // When: POST with short password
        mockMvc.post("/api/auth/register") {
            contentType = MediaType.APPLICATION_JSON
            content = objectMapper.writeValueAsString(request)
        }.andExpect {
            // Then: Returns 400 Bad Request with validation error
            status { isBadRequest() }
            jsonPath("$.error") { value("Validation Failed") }
        }
    }

    @Test
    fun `should login with valid credentials`() {
        // Given: Registered user
        val registerRequest = RegisterRequest(
            name = "John Doe",
            email = "john@example.com",
            password = "password123",
            role = "STAFF"
        )
        mockMvc.post("/api/auth/register") {
            contentType = MediaType.APPLICATION_JSON
            content = objectMapper.writeValueAsString(registerRequest)
        }

        // When: Login with correct credentials
        val loginRequest = LoginRequest(
            email = "john@example.com",
            password = "password123"
        )

        mockMvc.post("/api/auth/login") {
            contentType = MediaType.APPLICATION_JSON
            content = objectMapper.writeValueAsString(loginRequest)
        }.andExpect {
            // Then: Returns 200 OK with token
            status { isOk() }
            jsonPath("$.token") { exists() }
            jsonPath("$.email") { value("john@example.com") }
            jsonPath("$.role") { value("STAFF") }
        }
    }

    @Test
    fun `should reject login with invalid password`() {
        // Given: Registered user
        val registerRequest = RegisterRequest(
            name = "John Doe",
            email = "john@example.com",
            password = "password123",
            role = "STAFF"
        )
        mockMvc.post("/api/auth/register") {
            contentType = MediaType.APPLICATION_JSON
            content = objectMapper.writeValueAsString(registerRequest)
        }

        // When: Login with wrong password
        val loginRequest = LoginRequest(
            email = "john@example.com",
            password = "wrongpassword"
        )

        mockMvc.post("/api/auth/login") {
            contentType = MediaType.APPLICATION_JSON
            content = objectMapper.writeValueAsString(loginRequest)
        }.andExpect {
            // Then: Returns 400 Bad Request
            status { isBadRequest() }
            jsonPath("$.message") { value("Invalid email or password") }
        }
    }

    @Test
    fun `login should set HttpOnly refresh_token cookie and return access token in body`() {
        val register = RegisterRequest(
            name = "Cookie User",
            email = "cookie@example.com",
            password = "password123",
            role = "STAFF"
        )
        mockMvc.post("/api/auth/register") {
            contentType = MediaType.APPLICATION_JSON
            content = objectMapper.writeValueAsString(register)
        }

        val loginRequest = LoginRequest(email = "cookie@example.com", password = "password123")

        mockMvc.post("/api/auth/login") {
            contentType = MediaType.APPLICATION_JSON
            content = objectMapper.writeValueAsString(loginRequest)
        }.andExpect {
            status { isOk() }
            jsonPath("$.token") { exists() }
            // Refresh token must NOT leak into the JSON body
            jsonPath("$.refreshToken") { doesNotExist() }
            // Set-Cookie carries the refresh token with HttpOnly
            header {
                string(HttpHeaders.SET_COOKIE, containsString("refresh_token="))
                string(HttpHeaders.SET_COOKIE, containsString("HttpOnly"))
                string(HttpHeaders.SET_COOKIE, containsString("Path=/api/auth"))
                string(HttpHeaders.SET_COOKIE, containsString("SameSite=Lax"))
            }
            cookie { httpOnly("refresh_token", true) }
        }
    }

    @Test
    fun `refresh should return a new access token when a valid refresh cookie is provided`() {
        // Register to obtain a refresh cookie
        val register = RegisterRequest(
            name = "Refresh User",
            email = "refresh@example.com",
            password = "password123",
            role = "STAFF"
        )
        val registerResult = mockMvc.post("/api/auth/register") {
            contentType = MediaType.APPLICATION_JSON
            content = objectMapper.writeValueAsString(register)
        }.andReturn()

        val refreshCookieValue = registerResult.response.getCookie("refresh_token")?.value
        check(!refreshCookieValue.isNullOrBlank()) { "register did not set refresh_token cookie" }

        mockMvc.post("/api/auth/refresh") {
            cookie(Cookie("refresh_token", refreshCookieValue))
        }.andExpect {
            status { isOk() }
            jsonPath("$.token") { exists() }
            jsonPath("$.email") { value("refresh@example.com") }
            // Cookie is rotated
            header { string(HttpHeaders.SET_COOKIE, containsString("refresh_token=")) }
        }
    }

    @Test
    fun `refresh should fail when no cookie is provided`() {
        mockMvc.post("/api/auth/refresh") {
            contentType = MediaType.APPLICATION_JSON
        }.andExpect {
            status { isBadRequest() }
            jsonPath("$.message") { value("Missing refresh token") }
        }
    }

    @Test
    fun `refresh should fail when cookie value is invalid`() {
        mockMvc.post("/api/auth/refresh") {
            cookie(Cookie("refresh_token", "not-a-real-jwt"))
        }.andExpect {
            status { isBadRequest() }
        }
    }

    @Test
    fun `logout should clear the refresh cookie with Max-Age 0`() {
        mockMvc.post("/api/auth/logout").andExpect {
            status { isNoContent() }
            header {
                string(HttpHeaders.SET_COOKIE, containsString("refresh_token="))
                string(HttpHeaders.SET_COOKIE, containsString("Max-Age=0"))
                string(HttpHeaders.SET_COOKIE, containsString("HttpOnly"))
            }
        }
    }

    @Test
    fun `should reject login with non-existent email`() {
        // When: Login with email that doesn't exist
        val loginRequest = LoginRequest(
            email = "nonexistent@example.com",
            password = "password123"
        )

        mockMvc.post("/api/auth/login") {
            contentType = MediaType.APPLICATION_JSON
            content = objectMapper.writeValueAsString(loginRequest)
        }.andExpect {
            // Then: Returns 400 Bad Request
            status { isBadRequest() }
            jsonPath("$.message") { value("Invalid email or password") }
        }
    }
}
