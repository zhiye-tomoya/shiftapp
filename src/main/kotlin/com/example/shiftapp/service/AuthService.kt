package com.example.shiftapp.service

import com.example.shiftapp.domain.Role
import com.example.shiftapp.domain.User
import com.example.shiftapp.dto.request.LoginRequest
import com.example.shiftapp.dto.request.RegisterRequest
import com.example.shiftapp.dto.response.AuthResponse
import com.example.shiftapp.repository.UserRepository
import com.example.shiftapp.security.JwtUtil
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Service

/**
 * Service for handling user authentication.
 *
 * Responsibilities:
 * - Register new users (hash passwords!)
 * - Login users (verify passwords, generate JWT)
 * - Validate email uniqueness
 *
 * Security Notes:
 * - Passwords are NEVER stored in plain text
 * - We use BCrypt for password hashing (one-way, secure)
 * - Login failures should not reveal if email or password was wrong (security best practice)
 */
@Service
class AuthService(
    private val userRepository: UserRepository,
    private val passwordEncoder: PasswordEncoder,
    private val jwtUtil: JwtUtil
) {

    /**
     * Register a new user.
     *
     * Steps:
     * 1. Check if email already exists (reject if duplicate)
     * 2. Validate role (must be ADMIN or STAFF)
     * 3. Hash the password (NEVER store plain text!)
     * 4. Save user to database
     * 5. Generate JWT token
     * 6. Return token to client
     */
    fun register(request: RegisterRequest): AuthResponse {
        // Check if user already exists
        if (userRepository.existsByEmail(request.email)) {
            throw IllegalArgumentException("Email already exists")
        }

        // Validate role
        val role = try {
            Role.valueOf(request.role.uppercase())
        } catch (e: IllegalArgumentException) {
            throw IllegalArgumentException("Invalid role: ${request.role}. Must be ADMIN or STAFF")
        }

        // Create new user with hashed password
        val user = User(
            name = request.name,
            email = request.email,
            password = passwordEncoder.encode(request.password),  // Hash password!
            role = role
        )

        val savedUser = userRepository.save(user)

        // Generate JWT token
        val token = jwtUtil.generateToken(
            email = savedUser.email,
            userId = savedUser.id,
            role = savedUser.role.name
        )

        return AuthResponse(
            token = token,
            userId = savedUser.id,
            email = savedUser.email,
            role = savedUser.role.name
        )
    }

    /**
     * Login a user.
     *
     * Steps:
     * 1. Find user by email
     * 2. Verify password (compare hashed password with input)
     * 3. Generate JWT token if valid
     * 4. Return token to client
     *
     * Security Note:
     * We return "Invalid email or password" for both cases (email not found OR wrong password)
     * This prevents attackers from knowing which emails exist in the system.
     */
    fun login(request: LoginRequest): AuthResponse {
        // Find user by email
        val user = userRepository.findByEmail(request.email)
            ?: throw IllegalArgumentException("Invalid email or password")

        // Verify password
        // passwordEncoder.matches() compares the plain password with the hashed one
        if (!passwordEncoder.matches(request.password, user.password)) {
            throw IllegalArgumentException("Invalid email or password")
        }

        // Generate JWT token
        val token = jwtUtil.generateToken(
            email = user.email,
            userId = user.id,
            role = user.role.name
        )

        return AuthResponse(
            token = token,
            userId = user.id,
            email = user.email,
            role = user.role.name
        )
    }
}
