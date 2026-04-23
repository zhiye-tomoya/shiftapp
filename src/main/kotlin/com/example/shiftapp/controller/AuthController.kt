package com.example.shiftapp.controller

import com.example.shiftapp.dto.request.LoginRequest
import com.example.shiftapp.dto.request.RegisterRequest
import com.example.shiftapp.dto.response.AuthResponse
import com.example.shiftapp.service.AuthService
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

/**
 * Controller for authentication operations.
 *
 * Endpoints:
 * - POST /api/auth/register: Register a new user
 * - POST /api/auth/login: Login and get JWT token
 *
 * These endpoints are PUBLIC (no authentication required).
 * See SecurityConfig for configuration.
 */
@RestController
@RequestMapping("/api/auth")
class AuthController(
    private val authService: AuthService
) {

    /**
     * Register a new user.
     *
     * Request body: { name, email, password, role }
     * Response: { token, userId, email, role }
     * Status: 201 Created
     *
     * The @Valid annotation triggers validation on RegisterRequest fields.
     * If validation fails, Spring automatically returns 400 Bad Request.
     */
    @PostMapping("/register")
    fun register(@Valid @RequestBody request: RegisterRequest): ResponseEntity<AuthResponse> {
        val response = authService.register(request)
        return ResponseEntity.status(HttpStatus.CREATED).body(response)
    }

    /**
     * Login a user.
     *
     * Request body: { email, password }
     * Response: { token, userId, email, role }
     * Status: 200 OK
     *
     * Client should store the token and include it in future requests:
     * Authorization: Bearer <token>
     */
    @PostMapping("/login")
    fun login(@Valid @RequestBody request: LoginRequest): ResponseEntity<AuthResponse> {
        val response = authService.login(request)
        return ResponseEntity.ok(response)
    }
}
