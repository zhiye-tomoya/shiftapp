package com.example.shiftapp.dto.response

/**
 * Response DTO for authentication operations (register/login).
 *
 * Contains the JWT token that clients must include in subsequent requests
 * via the Authorization header: "Bearer {token}"
 */
data class AuthResponse(
    val token: String,
    val userId: Long,
    val email: String,
    val role: String
)
