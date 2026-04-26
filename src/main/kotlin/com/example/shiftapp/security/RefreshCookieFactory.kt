package com.example.shiftapp.security

import org.springframework.beans.factory.annotation.Value
import org.springframework.http.ResponseCookie
import org.springframework.stereotype.Component
import java.time.Duration

/**
 * Builds the HttpOnly cookie used to carry the refresh token.
 *
 * Cookie design choices:
 * - `HttpOnly = true`     : JavaScript cannot read it (mitigates XSS token theft).
 * - `Secure   = configurable` : true in production (HTTPS), false in local/test.
 * - `SameSite = Lax`      : sent on top-level navigations; reasonable default for
 *                           same-origin / first-party SPA setups.
 * - `Path     = /api/auth`: cookie is only attached to auth endpoints
 *                           (refresh / logout) – never leaks to other APIs.
 */
@Component
class RefreshCookieFactory(
    @Value("\${app.auth.refresh-cookie.name:refresh_token}")
    private val cookieName: String,

    @Value("\${app.auth.refresh-cookie.path:/api/auth}")
    private val cookiePath: String,

    @Value("\${app.auth.refresh-cookie.secure:false}")
    private val secure: Boolean,

    @Value("\${app.auth.refresh-cookie.same-site:Lax}")
    private val sameSite: String,

    @Value("\${jwt.refresh.expiration:604800000}")
    private val refreshExpirationMs: Long,
) {

    val name: String get() = cookieName

    /** Build the Set-Cookie value for issuing a refresh token. */
    fun build(refreshToken: String): ResponseCookie =
        ResponseCookie.from(cookieName, refreshToken)
            .httpOnly(true)
            .secure(secure)
            .sameSite(sameSite)
            .path(cookiePath)
            .maxAge(Duration.ofMillis(refreshExpirationMs))
            .build()

    /** Build the Set-Cookie value that immediately clears the refresh cookie. */
    fun clear(): ResponseCookie =
        ResponseCookie.from(cookieName, "")
            .httpOnly(true)
            .secure(secure)
            .sameSite(sameSite)
            .path(cookiePath)
            .maxAge(0)
            .build()
}
