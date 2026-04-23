package com.example.shiftapp.domain

/**
 * User roles in the system.
 *
 * ADMIN: Can approve/reject shifts and swap requests
 * STAFF: Can create shifts and request swaps
 */
enum class Role {
    ADMIN,
    STAFF
}
