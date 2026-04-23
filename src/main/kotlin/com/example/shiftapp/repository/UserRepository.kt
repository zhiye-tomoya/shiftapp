package com.example.shiftapp.repository

import com.example.shiftapp.domain.User
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

/**
 * Repository for User entity persistence.
 *
 * Spring Data JPA will automatically implement these methods:
 * - findByEmail: Find a user by their email address (for login)
 * - existsByEmail: Check if an email is already registered (for validation)
 */
@Repository
interface UserRepository : JpaRepository<User, Long> {
    /**
     * Find a user by email address.
     * Returns null if no user found.
     */
    fun findByEmail(email: String): User?

    /**
     * Check if a user with this email already exists.
     * Useful for registration validation.
     */
    fun existsByEmail(email: String): Boolean
}
