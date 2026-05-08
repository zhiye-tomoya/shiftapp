package com.example.shiftapp.security

import org.springframework.security.core.GrantedAuthority
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.userdetails.UserDetails

/**
 * Custom principal placed into the [org.springframework.security.core.context.SecurityContext]
 * by [JwtAuthenticationFilter] for every authenticated request.
 *
 * Why a custom principal?
 *  - We want controllers to obtain the caller's `userId` (Long) trivially via
 *    `@AuthenticationPrincipal AuthenticatedUser` — the JWT already carries it, so
 *    there is no reason to re-query the DB on every request.
 *  - Implementing [UserDetails] keeps us idiomatic with Spring Security:
 *      * `authentication.name` resolves to [email] via [getUsername]
 *      * Existing helpers like [com.example.shiftapp.security.JwtUtil.validateToken] keep working
 *
 * Identity vs authorisation:
 *  - [userId] / [email]    → identity claims pulled from the access token
 *  - [role]                → authorisation claim ("ADMIN" or "STAFF"); also encoded in
 *                            [getAuthorities] as the conventional `ROLE_<role>` for
 *                            `@PreAuthorize("hasRole('ADMIN')")` to recognise it.
 */
data class AuthenticatedUser(
    val userId: Long,
    val email: String,
    val role: String,
) : UserDetails {

    override fun getAuthorities(): Collection<GrantedAuthority> =
        listOf(SimpleGrantedAuthority("ROLE_$role"))

    /** No password is ever held in the security context for JWT-authenticated users. */
    override fun getPassword(): String? = null

    /** Spring Security's "username" — we map it to [email] for downstream code. */
    override fun getUsername(): String = email

    override fun isAccountNonExpired(): Boolean = true
    override fun isAccountNonLocked(): Boolean = true
    override fun isCredentialsNonExpired(): Boolean = true
    override fun isEnabled(): Boolean = true
}
