package com.example.shiftapp.domain

/**
 * Minimal Shift aggregate used by the service-layer TDD phase.
 *
 * No persistence annotations are applied here — JPA/DB integration is
 * intentionally deferred until after the core business rules are proven
 * out with unit tests.
 */
data class Shift(
    val id: Long,
    val status: ShiftStatus,
)
