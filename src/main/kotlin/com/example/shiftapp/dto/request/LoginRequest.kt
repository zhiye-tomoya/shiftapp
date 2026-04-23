package com.example.shiftapp.dto.request

import jakarta.validation.constraints.NotBlank

/**
 * Request DTO for user login.
 *
 * Client sends email and password to get a JWT token back.
 */
data class LoginRequest(
    @field:NotBlank(message = "Email is required")
    val email: String,

    @field:NotBlank(message = "Password is required")
    val password: String
)
