package com.example.shiftapp.domain

import jakarta.persistence.*

/**
 * User entity representing a system user.
 *
 * Each user has:
 * - A unique email (used for login)
 * - A hashed password (never stored in plain text!)
 * - A role (ADMIN or STAFF) that determines permissions
 * - A storeId (for multi-store support in the future)
 */
@Entity
@Table(name = "users")
data class User(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0L,

    @Column(nullable = false)
    val name: String,

    @Column(nullable = false, unique = true)
    val email: String,

    @Column(nullable = false)
    val password: String,  // Hashed password (BCrypt)

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    val role: Role,

    @Column(name = "store_id", nullable = false)
    val storeId: Long = 1L  // Default store for now
) {
    /**
     * Helper method to check if user is an admin.
     * Useful for authorization checks.
     */
    fun isAdmin(): Boolean = role == Role.ADMIN

    /**
     * Helper method to check if user is staff.
     */
    fun isStaff(): Boolean = role == Role.STAFF
}
