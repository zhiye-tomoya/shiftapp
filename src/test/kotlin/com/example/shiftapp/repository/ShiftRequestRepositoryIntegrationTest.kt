package com.example.shiftapp.repository

import com.example.shiftapp.domain.Shift
import com.example.shiftapp.domain.ShiftRequest
import com.example.shiftapp.domain.ShiftStatus
import com.example.shiftapp.domain.RequestStatus
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager
import org.springframework.test.context.ActiveProfiles
import java.time.LocalDateTime
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@DataJpaTest
@ActiveProfiles("test")
class ShiftRequestRepositoryIntegrationTest {

    @Autowired
    private lateinit var shiftRequestRepository: ShiftRequestRepository

    @Autowired
    private lateinit var entityManager: TestEntityManager

    /**
     * Helper: persist a Shift first, since `ShiftRequest.shift` is a
     * `@ManyToOne` without cascade. Saving a request whose Shift is still
     * transient throws `TransientPropertyValueException`.
     */
    private fun persistShift(userId: Long): Shift = entityManager.persistAndFlush(
        Shift(
            status = ShiftStatus.APPROVED,
            userId = userId,
            clockInTime = LocalDateTime.of(2025, 1, 15, 9, 0),
            clockOutTime = LocalDateTime.of(2025, 1, 15, 17, 0)
        )
    )

    @Test
    fun `should save and retrieve shift request`() {
        // Given
        val shift = persistShift(userId = 100L)
        val request = ShiftRequest(
            shift = shift,
            requesterId = 100L,
            targetUserId = 200L,
            status = RequestStatus.PENDING
        )

        // When
        val saved = shiftRequestRepository.save(request)

        // Then
        assertNotNull(saved.id)
        assertTrue(saved.id > 0)
        assertEquals(RequestStatus.PENDING, saved.status)
        assertEquals(100L, saved.requesterId)
        assertEquals(200L, saved.targetUserId)
    }

    @Test
    fun `should find requests by requester id`() {
        // Given
        val shift1 = persistShift(userId = 100L)
        val shift2 = persistShift(userId = 100L)

        shiftRequestRepository.save(
            ShiftRequest(shift = shift1, requesterId = 100L, targetUserId = 200L, status = RequestStatus.PENDING)
        )
        shiftRequestRepository.save(
            ShiftRequest(shift = shift2, requesterId = 100L, targetUserId = 300L, status = RequestStatus.TARGET_APPROVED)
        )

        // When
        val requests = shiftRequestRepository.findAllByRequesterId(100L)

        // Then
        assertEquals(2, requests.size)
        assertTrue(requests.all { it.requesterId == 100L })
    }

    @Test
    fun `should find requests by status`() {
        // Given
        val shift1 = persistShift(userId = 100L)
        val shift2 = persistShift(userId = 200L)

        shiftRequestRepository.save(
            ShiftRequest(shift = shift1, requesterId = 100L, targetUserId = 200L, status = RequestStatus.PENDING)
        )
        shiftRequestRepository.save(
            ShiftRequest(shift = shift2, requesterId = 200L, targetUserId = 300L, status = RequestStatus.PENDING)
        )

        // When
        val pending = shiftRequestRepository.findAllByStatus(RequestStatus.PENDING)

        // Then
        assertEquals(2, pending.size)
        assertTrue(pending.all { it.status == RequestStatus.PENDING })
    }
}
