package com.example.shiftapp.security

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity
import org.springframework.security.config.http.SessionCreationPolicy
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.security.web.SecurityFilterChain
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter

/**
 * Spring Security Configuration.
 *
 * This configures how our application is secured:
 * - JWT-based authentication (stateless, no sessions)
 * - Public endpoints (register, login, swagger)
 * - Protected endpoints (require valid JWT token)
 * - Role-based access control (ADMIN vs STAFF)
 *
 * Key Concepts:
 * - CSRF disabled: Not needed for stateless JWT APIs
 * - Stateless sessions: No server-side session storage
 * - Filter chain: Our JWT filter runs before Spring's authentication
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity(prePostEnabled = true)  // Enables @PreAuthorize annotations
class SecurityConfig(
    private val jwtAuthenticationFilter: JwtAuthenticationFilter
) {

    /**
     * Configure the security filter chain.
     *
     * This defines:
     * - Which endpoints are public (no authentication needed)
     * - Which endpoints require authentication
     * - Which endpoints require specific roles
     */
    @Bean
    fun securityFilterChain(http: HttpSecurity): SecurityFilterChain {
        http
            // Disable CSRF (Cross-Site Request Forgery)
            // We don't need it because JWT tokens can't be stolen via CSRF attacks
            .csrf { it.disable() }

            // Configure authorization rules
            .authorizeHttpRequests { auth ->
                auth
                    // Public endpoints - anyone can access
                    .requestMatchers("/api/auth/**").permitAll()  // Register, login
                    .requestMatchers("/swagger-ui/**", "/v3/api-docs/**").permitAll()  // API docs
                    .requestMatchers("/actuator/health").permitAll()  // Health check

                    // Admin-only endpoints
                    .requestMatchers("/api/admin/**").hasRole("ADMIN")

                    // All other endpoints require authentication
                    .anyRequest().authenticated()
            }

            // Configure session management
            // STATELESS: No server-side sessions, JWT token contains all info
            .sessionManagement { session ->
                session.sessionCreationPolicy(SessionCreationPolicy.STATELESS)
            }

            // Add our JWT filter BEFORE Spring Security's authentication filter
            // This way, the JWT filter runs first and sets up authentication
            .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter::class.java)

        return http.build()
    }

    /**
     * Password encoder bean.
     *
     * BCrypt is a strong, slow hashing algorithm designed for passwords.
     * - One-way: Can't reverse engineer the password
     * - Salt: Each password has unique salt (prevents rainbow table attacks)
     * - Adaptive: Can increase complexity as computers get faster
     */
    @Bean
    fun passwordEncoder(): PasswordEncoder {
        return BCryptPasswordEncoder()
    }
}
