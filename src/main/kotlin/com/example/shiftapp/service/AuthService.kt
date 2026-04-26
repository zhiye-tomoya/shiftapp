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
 * Result bundle returned by auth flows.
 *
 * - [auth]         : JSON body returned to the client (contains the access token).
 * - [refreshToken] : the refresh token string – the controller is responsible
 *                    for placing it inside an HttpOnly cookie. It must NEVER
 *                    be put inside the JSON body.
 */
data class AuthTokens(
    val auth: AuthResponse,
    val refreshToken: String,
)

/**
 * Service for handling user authentication.
 *
 * Token strategy:
 * - access  → returned in JSON body, kept in front-end memory
 * - refresh → returned via HttpOnly cookie, used to obtain a new access token
 */
@Service
class AuthService(
    private val userRepository: UserRepository,
    private val passwordEncoder: PasswordEncoder,
    private val jwtUtil: JwtUtil,
) {

    /**
     * Register a new user and immediately issue tokens.
     */
    fun register(request: RegisterRequest): AuthTokens {
        if (userRepository.existsByEmail(request.email)) {
            throw IllegalArgumentException("Email already exists")
        }

        val role = try {
            Role.valueOf(request.role.uppercase())
        } catch (e: IllegalArgumentException) {
            throw IllegalArgumentException("Invalid role: ${request.role}. Must be ADMIN or STAFF")
        }

        val user = User(
            name = request.name,
            email = request.email,
            password = passwordEncoder.encode(request.password),
            role = role
        )

        val saved = userRepository.save(user)
        return issueTokens(saved)
    }

    /**
     * Verify credentials and issue tokens.
     */
    fun login(request: LoginRequest): AuthTokens {
        val user = userRepository.findByEmail(request.email)
            ?: throw IllegalArgumentException("Invalid email or password")

        if (!passwordEncoder.matches(request.password, user.password)) {
            throw IllegalArgumentException("Invalid email or password")
        }

        return issueTokens(user)
    }

    /**
     * Exchange a valid refresh token for a fresh access token.
     *
     * We also rotate the refresh token (issue a new one) so that
     * the next refresh requires the latest cookie. This is a simple
     * mitigation for token replay – a fully-fledged solution would
     * persist a refresh-token id ("jti") server-side.
     */
    fun refresh(refreshToken: String): AuthTokens {
        if (!jwtUtil.validateRefreshToken(refreshToken)) {
            throw IllegalArgumentException("Invalid or expired refresh token")
        }

        val email = jwtUtil.extractEmail(refreshToken)
        val user = userRepository.findByEmail(email)
            ?: throw IllegalArgumentException("Invalid refresh token")

        return issueTokens(user)
    }

    private fun issueTokens(user: User): AuthTokens {
        val access = jwtUtil.generateAccessToken(
            email = user.email,
            userId = user.id,
            role = user.role.name
        )
        val refresh = jwtUtil.generateRefreshToken(
            email = user.email,
            userId = user.id,
            role = user.role.name
        )
        val body = AuthResponse(
            token = access,
            userId = user.id,
            email = user.email,
            role = user.role.name
        )
        return AuthTokens(auth = body, refreshToken = refresh)
    }
}
