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
 * Two token kinds:
 * - access  : short-lived. Stored in front-end memory and sent via Authorization header.
 * - refresh : long-lived.  Stored in HttpOnly Cookie and used to obtain a new access token.
 *
 * The "type" custom claim distinguishes them so a refresh token cannot
 * be (mis)used as an access token and vice versa.
 */
@Component
class JwtUtil {

    @Value("\${jwt.secret}")
    private lateinit var secret: String

    /** Access token TTL (ms). Default: 15 minutes. */
    @Value("\${jwt.access.expiration:900000}")
    private var accessExpiration: Long = 900_000

    /** Refresh token TTL (ms). Default: 7 days. */
    @Value("\${jwt.refresh.expiration:604800000}")
    private var refreshExpiration: Long = 604_800_000

    companion object {
        const val TYPE_ACCESS = "access"
        const val TYPE_REFRESH = "refresh"
        private const val CLAIM_TYPE = "type"
        private const val CLAIM_USER_ID = "userId"
        private const val CLAIM_ROLE = "role"
    }

    private fun getSigningKey(): SecretKey =
        Keys.hmacShaKeyFor(secret.toByteArray())

    // --------------------------------------------------------------------
    // Generation
    // --------------------------------------------------------------------

    /**
     * Generate an access token (short-lived, returned in JSON body).
     */
    fun generateAccessToken(email: String, userId: Long, role: String): String {
        val claims = mapOf(
            CLAIM_USER_ID to userId,
            CLAIM_ROLE to role,
            CLAIM_TYPE to TYPE_ACCESS
        )
        return buildToken(email, claims, accessExpiration)
    }

    /**
     * Generate a refresh token (long-lived, returned via HttpOnly cookie).
     * Role is included so we can re-issue an access token without a DB lookup,
     * but the front-end never sees this token directly.
     */
    fun generateRefreshToken(email: String, userId: Long, role: String): String {
        val claims = mapOf(
            CLAIM_USER_ID to userId,
            CLAIM_ROLE to role,
            CLAIM_TYPE to TYPE_REFRESH
        )
        return buildToken(email, claims, refreshExpiration)
    }

    /**
     * Backwards-compatible alias used by older code paths / tests.
     * Equivalent to [generateAccessToken].
     */
    fun generateToken(email: String, userId: Long, role: String): String =
        generateAccessToken(email, userId, role)

    private fun buildToken(subject: String, claims: Map<String, Any>, ttlMillis: Long): String {
        val now = Date()
        return Jwts.builder()
            .subject(subject)
            .claims(claims)
            .issuedAt(now)
            .expiration(Date(now.time + ttlMillis))
            .signWith(getSigningKey())
            .compact()
    }

    // --------------------------------------------------------------------
    // Extraction
    // --------------------------------------------------------------------

    fun extractEmail(token: String): String =
        extractAllClaims(token).subject

    fun extractUserId(token: String): Long {
        val userId = extractAllClaims(token)[CLAIM_USER_ID]
        return when (userId) {
            is Number -> userId.toLong()
            else -> throw IllegalStateException("userId claim is not a number")
        }
    }

    fun extractRole(token: String): String =
        extractAllClaims(token)[CLAIM_ROLE] as String

    fun extractTokenType(token: String): String =
        (extractAllClaims(token)[CLAIM_TYPE] as? String) ?: TYPE_ACCESS

    // --------------------------------------------------------------------
    // Validation
    // --------------------------------------------------------------------

    /**
     * Validate an access token against a UserDetails record.
     */
    fun validateToken(token: String, userDetails: UserDetails): Boolean {
        val email = extractEmail(token)
        return email == userDetails.username &&
            !isTokenExpired(token) &&
            extractTokenType(token) == TYPE_ACCESS
    }

    /**
     * Validate a refresh token. Returns true only if signature is valid,
     * the token is not expired, and the type claim is "refresh".
     */
    fun validateRefreshToken(token: String): Boolean = try {
        !isTokenExpired(token) && extractTokenType(token) == TYPE_REFRESH
    } catch (e: Exception) {
        false
    }

    private fun extractAllClaims(token: String): Claims =
        Jwts.parser()
            .verifyWith(getSigningKey())
            .build()
            .parseSignedClaims(token)
            .payload

    private fun isTokenExpired(token: String): Boolean =
        extractAllClaims(token).expiration.before(Date())
}
