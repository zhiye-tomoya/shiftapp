package com.example.shiftapp.domain

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import java.time.DayOfWeek
import java.time.LocalTime

/**
 * Pure unit tests for the [ShiftTemplate] domain invariants.
 *
 * Kept off the Spring boot context — these are simple value-object checks
 * that should never need a database, mocks, or DI to run.
 */
class ShiftTemplateTest {

    @Test
    fun `init rejects clockOut not strictly after clockIn`() {
        assertThatThrownBy {
            ShiftTemplate(
                name = "Weekday day shift",
                clockInLocalTime = LocalTime.of(9, 0),
                clockOutLocalTime = LocalTime.of(9, 0),
                daysOfWeek = setOf(DayOfWeek.MONDAY),
            )
        }.isInstanceOf(IllegalArgumentException::class.java)
            .hasMessageContaining("clockOutLocalTime must be after clockInLocalTime")

        assertThatThrownBy {
            ShiftTemplate(
                name = "Weekday day shift",
                clockInLocalTime = LocalTime.of(10, 0),
                clockOutLocalTime = LocalTime.of(9, 0),
                daysOfWeek = setOf(DayOfWeek.MONDAY),
            )
        }.isInstanceOf(IllegalArgumentException::class.java)
    }

    @Test
    fun `init rejects empty daysOfWeek`() {
        assertThatThrownBy {
            ShiftTemplate(
                name = "Anything",
                clockInLocalTime = LocalTime.of(9, 0),
                clockOutLocalTime = LocalTime.of(18, 0),
                daysOfWeek = emptySet(),
            )
        }.isInstanceOf(IllegalArgumentException::class.java)
            .hasMessageContaining("daysOfWeek must not be empty")
    }

    @Test
    fun `init rejects blank name`() {
        assertThatThrownBy {
            ShiftTemplate(
                name = "  ",
                clockInLocalTime = LocalTime.of(9, 0),
                clockOutLocalTime = LocalTime.of(18, 0),
                daysOfWeek = setOf(DayOfWeek.MONDAY),
            )
        }.isInstanceOf(IllegalArgumentException::class.java)
            .hasMessageContaining("name must not be blank")
    }

    @Test
    fun `isGlobal mirrors ownerId == null`() {
        val global = ShiftTemplate(
            name = "Weekday day shift",
            clockInLocalTime = LocalTime.of(9, 0),
            clockOutLocalTime = LocalTime.of(18, 0),
            daysOfWeek = setOf(DayOfWeek.MONDAY),
            ownerId = null,
        )
        val personal = global.copy(ownerId = 42L)

        assertThat(global.isGlobal()).isTrue()
        assertThat(personal.isGlobal()).isFalse()
    }
}
