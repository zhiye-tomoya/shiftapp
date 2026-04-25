package com.example.shiftapp.repository

import com.example.shiftapp.domain.Shift
import com.example.shiftapp.domain.ShiftStatus
import java.time.LocalDateTime
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest
import org.springframework.test.context.ActiveProfiles
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@DataJpaTest
@ActiveProfiles("test")
class ShiftRepositoryIntegrationTest {

    @Autowired
    private lateinit var shiftRepository: ShiftRepository

    @Test
    fun `should save and retrieve shift`() {
        // Given
        val shift = Shift(
            status = ShiftStatus.DRAFT,
            userId = 100L,
            clockInTime = LocalDateTime.of(2025, 1, 15, 9, 0),
            clockOutTime = LocalDateTime.of(2025, 1, 15, 17, 0),
        )

        // When
        val savedShift = shiftRepository.save(shift)

        // Then
        assertNotNull(savedShift.id)
        assertTrue(savedShift.id > 0)
        assertEquals(ShiftStatus.DRAFT, savedShift.status)
        assertEquals(100L, savedShift.userId)
    }

    @Test
    fun `should find shift by id`() {
        // Given
        val shift = shiftRepository.save(
            Shift(status = ShiftStatus.APPROVED, userId = 200L, clockInTime = java.time.LocalDateTime.of(2025, 1, 15, 9, 0), clockOutTime = java.time.LocalDateTime.of(2025, 1, 15, 17, 0))
        )

        // When
        val found = shiftRepository.findById(shift.id)

        // Then
        assertTrue(found.isPresent)
        assertEquals(shift.id, found.get().id)
        assertEquals(ShiftStatus.APPROVED, found.get().status)
    }

    @Test
    fun `should find all shifts by userId`() {
        // Given
        shiftRepository.save(Shift(status = ShiftStatus.DRAFT, userId = 100L, clockInTime = java.time.LocalDateTime.of(2025, 1, 15, 9, 0), clockOutTime = java.time.LocalDateTime.of(2025, 1, 15, 17, 0)))
        shiftRepository.save(Shift(status = ShiftStatus.APPROVED, userId = 100L, clockInTime = java.time.LocalDateTime.of(2025, 1, 15, 9, 0), clockOutTime = java.time.LocalDateTime.of(2025, 1, 15, 17, 0)))
        shiftRepository.save(Shift(status = ShiftStatus.DRAFT, userId = 200L, clockInTime = java.time.LocalDateTime.of(2025, 1, 15, 9, 0), clockOutTime = java.time.LocalDateTime.of(2025, 1, 15, 17, 0)))

        // When
        val shifts = shiftRepository.findAllByUserId(100L)

        // Then
        assertEquals(2, shifts.size)
        assertTrue(shifts.all { it.userId == 100L })
    }

    @Test
    fun `should update shift status`() {
        // Given
        val shift = shiftRepository.save(
            Shift(status = ShiftStatus.DRAFT, userId = 100L, clockInTime = java.time.LocalDateTime.of(2025, 1, 15, 9, 0), clockOutTime = java.time.LocalDateTime.of(2025, 1, 15, 17, 0))
        )

        // When
        val submitted = shift.submit()
        val updated = shiftRepository.save(submitted)

        // Then
        assertEquals(shift.id, updated.id)
        assertEquals(ShiftStatus.SUBMITTED, updated.status)
    }
}
