package com.example.shiftapp.security

import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.web.cors.CorsConfiguration
import org.springframework.web.cors.CorsConfigurationSource
import org.springframework.web.cors.UrlBasedCorsConfigurationSource

/**
 * CORS configuration for the SPA front-end (Next.js on Vercel).
 *
 * The browser blocks cross-origin requests from the Vercel domain to this
 * Railway-hosted API unless the server explicitly allows the origin. Because
 * the refresh token travels in a cookie, we must:
 *   - allow the exact front-end origin(s) — wildcard "*" is NOT permitted when
 *     credentials are allowed, and
 *   - set allowCredentials = true so the browser sends/receives the cookie.
 *
 * Origins are configured via `app.cors.allowed-origins` (comma-separated) so
 * they can be overridden per environment without code changes:
 *
 *   APP_CORS_ALLOWED_ORIGINS=https://your-app.vercel.app,https://www.yourdomain.com
 *
 * Tip: `allowed-origin-patterns` is used (not `allowed-origins`) so you can
 * also match Vercel preview deployments with a pattern, e.g.
 *   https://your-app-*.vercel.app
 */
@Configuration
class CorsConfig(
    @Value("\${app.cors.allowed-origins:http://localhost:3000}")
    private val allowedOrigins: String,
) {

    @Bean
    fun corsConfigurationSource(): CorsConfigurationSource {
        // Pattern-based so wildcards (e.g. Vercel preview URLs) are supported.
        val originPatterns: List<String> = allowedOrigins
            .split(",")
            .map(String::trim)
            .filter(String::isNotEmpty)

        val config = CorsConfiguration().apply {
            allowedOriginPatterns = originPatterns
            allowedMethods = listOf("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS")
            allowedHeaders = listOf("*")
            // Required for the HttpOnly refresh cookie to be sent/received.
            allowCredentials = true
            // Cache the CORS preflight response for 1 hour.
            maxAge = 3600L
        }

        return UrlBasedCorsConfigurationSource().apply {
            registerCorsConfiguration("/**", config)
        }
    }
}
