package com.example.shiftapp.dto.request

import jakarta.validation.constraints.Email
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size

/**
 * Request DTO for user registration.
 *
 * Client provides name, email, password, and role (ADMIN or STAFF).
 * Password will be hashed before storing in the database.
 */
data class RegisterRequest(
    @field:NotBlank(message = "Name is required")
    val name: String,

    @field:NotBlank(message = "Email is required")
    @field:Email(message = "Email must be valid")
    val email: String,

    @field:NotBlank(message = "Password is required")
    @field:Size(min = 8, message = "Password must be at least 8 characters")
    val password: String,

    @field:NotBlank(message = "Role is required")
    val role: String  // "ADMIN" or "STAFF"
)
