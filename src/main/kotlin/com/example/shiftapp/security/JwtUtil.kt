package com.example.shiftapp.security

import io.jsonwebtoken.Claims
import io.jsonwebtoken.Jwts
import io.jsonwebtoken.security.Keys
import org.springframework.beans.factory.annotation.Value
import org.springframework.security.core.userdetails.UserDetails
import org.springframework.stereotype.Component
import java.util.*
import javax.crypto.SecretKey

/**
 * Utility class for JWT token operations.
 *
 * Responsibilities:
 * - Generate JWT tokens when users log in
 * - Extract information from tokens (email, userId, role)
 * - Validate tokens on each request
 *
 * JWT Structure: header.payload.signature
 * - Header: Algorithm info (HS256)
 * - Payload: User data (email, userId, role, expiration)
 * - Signature: Proves token wasn't tampered with
 */
@Component
class JwtUtil {

    @Value("\${jwt.secret}")
    private lateinit var secret: String

    @Value("\${jwt.expiration}")
    private var expiration: Long = 86400000 // 24 hours in milliseconds

    /**
     * Create a signing key from our secret string.
     * This key is used to sign and verify tokens.
     */
    private fun getSigningKey(): SecretKey {
        return Keys.hmacShaKeyFor(secret.toByteArray())
    }

    /**
     * Generate a JWT token for a user.
     *
     * The token contains:
     * - subject: user's email
     * - custom claims: userId and role
     * - issuedAt: when token was created
     * - expiration: when token expires (24 hours from now)
     */
    fun generateToken(email: String, userId: Long, role: String): String {
        val claims = mapOf(
            "userId" to userId,
            "role" to role
        )

        return Jwts.builder()
            .subject(email)
            .claims(claims)
            .issuedAt(Date())
            .expiration(Date(System.currentTimeMillis() + expiration))
            .signWith(getSigningKey())
            .compact()
    }

    /**
     * Extract the email (subject) from a token.
     */
    fun extractEmail(token: String): String {
        return extractAllClaims(token).subject
    }

    /**
     * Extract the userId from a token's custom claims.
     * JWT stores numbers as Number type, so we convert to Long.
     */
    fun extractUserId(token: String): Long {
        val userId = extractAllClaims(token)["userId"]
        return when (userId) {
            is Number -> userId.toLong()
            else -> throw IllegalStateException("userId claim is not a number")
        }
    }

    /**
     * Extract the role from a token's custom claims.
     */
    fun extractRole(token: String): String {
        return extractAllClaims(token)["role"] as String
    }

    /**
     * Validate a token:
     * 1. Email matches the UserDetails
     * 2. Token is not expired
     */
    fun validateToken(token: String, userDetails: UserDetails): Boolean {
        val email = extractEmail(token)
        return email == userDetails.username && !isTokenExpired(token)
    }

    /**
     * Parse and extract all claims from a token.
     * This verifies the signature and throws an exception if invalid.
     */
    private fun extractAllClaims(token: String): Claims {
        return Jwts.parser()
            .verifyWith(getSigningKey())
            .build()
            .parseSignedClaims(token)
            .payload
    }

    /**
     * Check if a token has expired.
     */
    private fun isTokenExpired(token: String): Boolean {
        return extractAllClaims(token).expiration.before(Date())
    }
}
