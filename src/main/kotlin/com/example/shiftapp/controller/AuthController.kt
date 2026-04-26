package com.example.shiftapp.controller

import com.example.shiftapp.dto.request.LoginRequest
import com.example.shiftapp.dto.request.RegisterRequest
import com.example.shiftapp.dto.response.AuthResponse
import com.example.shiftapp.security.RefreshCookieFactory
import com.example.shiftapp.service.AuthService
import com.example.shiftapp.service.AuthTokens
import jakarta.validation.Valid
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

/**
 * Controller for authentication operations.
 *
 * Token transport contract (must match front-end):
 * - access token  → JSON body field `token` (front-end keeps it in memory).
 * - refresh token → HttpOnly cookie (`refresh_token` by default, scoped to /api/auth).
 *
 * Endpoints:
 * - POST /api/auth/register : create a user, issue tokens
 * - POST /api/auth/login    : verify credentials, issue tokens
 * - POST /api/auth/refresh  : exchange the refresh cookie for a new access token (rotates the cookie)
 * - POST /api/auth/logout   : clear the refresh cookie
 *
 * All four endpoints are PUBLIC (no Authorization header required).
 */
@RestController
@RequestMapping("/api/auth")
class AuthController(
    private val authService: AuthService,
    private val refreshCookieFactory: RefreshCookieFactory,
) {

    @PostMapping("/register")
    fun register(@Valid @RequestBody request: RegisterRequest): ResponseEntity<AuthResponse> {
        val tokens = authService.register(request)
        return respondWithTokens(tokens, HttpStatus.CREATED)
    }

    @PostMapping("/login")
    fun login(@Valid @RequestBody request: LoginRequest): ResponseEntity<AuthResponse> {
        val tokens = authService.login(request)
        return respondWithTokens(tokens, HttpStatus.OK)
    }

    /**
     * Exchange the refresh-token cookie for a new access token.
     *
     * The refresh token is read from the HttpOnly cookie set at login time;
     * it is never accepted in the request body or query string.
     * A new refresh token is issued (rotation) and written back as a cookie.
     */
    @PostMapping("/refresh")
    fun refresh(
        @CookieValue(name = "\${app.auth.refresh-cookie.name:refresh_token}", required = false)
        refreshToken: String?,
    ): ResponseEntity<AuthResponse> {
        if (refreshToken.isNullOrBlank()) {
            throw IllegalArgumentException("Missing refresh token")
        }
        val tokens = authService.refresh(refreshToken)
        return respondWithTokens(tokens, HttpStatus.OK)
    }

    /**
     * Clear the refresh-token cookie.
     *
     * Returns 204 No Content. The front-end should also drop the in-memory
     * access token at the same time.
     */
    @PostMapping("/logout")
    fun logout(): ResponseEntity<Void> =
        ResponseEntity.noContent()
            .header(HttpHeaders.SET_COOKIE, refreshCookieFactory.clear().toString())
            .build()

    /** Common helper: write the access token in the body and the refresh token as a Set-Cookie. */
    private fun respondWithTokens(tokens: AuthTokens, status: HttpStatus): ResponseEntity<AuthResponse> {
        val cookie = refreshCookieFactory.build(tokens.refreshToken)
        return ResponseEntity.status(status)
            .header(HttpHeaders.SET_COOKIE, cookie.toString())
            .body(tokens.auth)
    }
}
