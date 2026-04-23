package com.example.shiftapp.security

import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter

/**
 * JWT Authentication Filter.
 *
 * This filter runs on EVERY request and:
 * 1. Extracts JWT token from Authorization header
 * 2. Validates the token
 * 3. Sets up Spring Security context so the rest of the app knows who's logged in
 *
 * Flow:
 * Request comes in → Filter extracts token → Token validated → User authenticated
 *
 * If no token or invalid token → User remains anonymous (unauthenticated)
 */
@Component
class JwtAuthenticationFilter(
    private val jwtUtil: JwtUtil
) : OncePerRequestFilter() {

    /**
     * This method is called for every request.
     *
     * We check for the Authorization header, extract the JWT,
     * and set up the authentication in Spring Security context.
     */
    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain
    ) {
        // 1. Extract Authorization header
        val authHeader = request.getHeader("Authorization")

        // 2. Check if header exists and starts with "Bearer "
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            // No token found, continue to next filter
            // User will be anonymous (not authenticated)
            filterChain.doFilter(request, response)
            return
        }

        try {
            // 3. Extract the token (remove "Bearer " prefix)
            val jwt = authHeader.substring(7)

            // 4. Extract user info from token
            val email = jwtUtil.extractEmail(jwt)
            val role = jwtUtil.extractRole(jwt)

            // 5. If not already authenticated, set up authentication
            if (SecurityContextHolder.getContext().authentication == null) {
                // Create authorities (Spring Security needs "ROLE_" prefix)
                val authorities = listOf(SimpleGrantedAuthority("ROLE_$role"))

                // Create authentication token
                val authToken = UsernamePasswordAuthenticationToken(
                    email,
                    null,
                    authorities
                )

                // Add request details
                authToken.details = WebAuthenticationDetailsSource().buildDetails(request)

                // Set authentication in Security Context
                // Now Spring Security knows who this user is!
                SecurityContextHolder.getContext().authentication = authToken
            }
        } catch (e: Exception) {
            // Token validation failed (expired, invalid signature, etc.)
            logger.error("JWT validation error: ${e.message}")
            // We don't stop the request, just log the error
            // User will remain unauthenticated
        }

        // Continue to next filter in the chain
        filterChain.doFilter(request, response)
    }
}
