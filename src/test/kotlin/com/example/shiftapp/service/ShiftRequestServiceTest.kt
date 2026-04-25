package com.example.shiftapp.service

import com.example.shiftapp.domain.Shift
import com.example.shiftapp.domain.ShiftRequest
import com.example.shiftapp.domain.RequestStatus
import com.example.shiftapp.domain.ShiftStatus
import com.example.shiftapp.repository.ShiftRepository
import com.example.shiftapp.repository.ShiftRequestRepository
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.util.Optional
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ShiftRequestServiceTest {

    private val shiftRepository: ShiftRepository = mockk()
    private val shiftRequestRepository: ShiftRequestRepository = mockk()
    private val shiftRequestService = ShiftRequestService(shiftRepository, shiftRequestRepository)

    @Test
    fun should_create_and_save_new_request_when_create_request_is_called() {
        val shift = Shift(id = 1L, status = ShiftStatus.APPROVED, userId = 100L, clockInTime = java.time.LocalDateTime.of(2025, 1, 15, 9, 0), clockOutTime = java.time.LocalDateTime.of(2025, 1, 15, 17, 0))
        every { shiftRepository.findById(1L) } returns Optional.of(shift)
        every { shiftRequestRepository.save(any()) } answers { firstArg() }

        val result = shiftRequestService.createRequest(requesterId = shift.userId, shiftId = 1L, targetUserId = 300L)

        assertEquals(RequestStatus.PENDING, result.status)
        assertEquals(shift.userId, result.requesterId)
        assertEquals(1L, result.shift.id)
        assertEquals(300L, result.targetUserId)
    }

    @Test
    fun should_retrieve_approve_and_save_request_when_approved_by_target_user() {
        val shift = Shift(id = 1L, status = ShiftStatus.APPROVED, userId = 100L, clockInTime = java.time.LocalDateTime.of(2025, 1, 15, 9, 0), clockOutTime = java.time.LocalDateTime.of(2025, 1, 15, 17, 0))
        val shiftRequest = ShiftRequest(
            id = 1L,
            shift = shift,
            requesterId = shift.userId,
            targetUserId = 200L,
            status = RequestStatus.PENDING
        )
        every { shiftRequestRepository.findById(1L) } returns Optional.of(shiftRequest)
        every { shiftRequestRepository.save(any()) } answers { firstArg() }

        val result = shiftRequestService.approveByTargetUser(shiftRequest.id)

        assertEquals(RequestStatus.TARGET_APPROVED, result.status)
        assertEquals(100L, result.shift.userId) // Shift ownership stays with requester (not transferred yet
        assertEquals(100L, result.requesterId) // Requester remains unchanged
        assertEquals(200L, result.targetUserId) // Target user remains unchanged
    }

    @Test
    fun should_retrieve_reject_and_save_request_when_rejected_by_target_user() {
        val shift = Shift(id = 1L, status = ShiftStatus.APPROVED, userId = 100L, clockInTime = java.time.LocalDateTime.of(2025, 1, 15, 9, 0), clockOutTime = java.time.LocalDateTime.of(2025, 1, 15, 17, 0))
        val shiftRequest = ShiftRequest(
            id =1L,
            shift = shift,
            requesterId = shift.userId,
            targetUserId = 200L,
            status = RequestStatus.PENDING
        )
        every { shiftRequestRepository.findById(1L) } returns Optional.of(shiftRequest)
        every { shiftRequestRepository.save(any()) } answers { firstArg() }

        val result = shiftRequestService.rejectByTargetUser(shiftRequest.id)

        assertEquals(RequestStatus.TARGET_REJECTED, result.status)
        assertEquals(100L, result.shift.userId) // Ownership stays with requester
        assertEquals(100L, result.requesterId) 
        assertEquals(200L, result.targetUserId) 
    }

    @Test
    fun should_retrieve_approve_and_save_request_when_approved_by_admin() {
        val shift = Shift(id = 1L, status = ShiftStatus.APPROVED, userId = 100L, clockInTime = java.time.LocalDateTime.of(2025, 1, 15, 9, 0), clockOutTime = java.time.LocalDateTime.of(2025, 1, 15, 17, 0))
        val shiftRequest = ShiftRequest(
            id = 1L,
            shift = shift,
            requesterId = shift.userId,
            targetUserId = 200L,
            status = RequestStatus.TARGET_APPROVED
        )
        every { shiftRequestRepository.findById(1L) } returns Optional.of(shiftRequest)
        every { shiftRequestRepository.save(any()) } answers { firstArg() } 

        val result = shiftRequestService.approveByAdmin(shiftRequest.id)

        assertEquals(RequestStatus.ADMIN_APPROVED, result.status)
        assertEquals(200L, result.shift.userId) // Ownership NOW transfers to target user on admin approval
        assertEquals(100L, result.requesterId) 
        assertEquals(200L, result.targetUserId)
    }

    @Test
    fun should_retrieve_reject_and_save_request_when_rejected_by_admin() {
        val shift = Shift(id = 1L, status = ShiftStatus.APPROVED, userId = 100L, clockInTime = java.time.LocalDateTime.of(2025, 1, 15, 9, 0), clockOutTime = java.time.LocalDateTime.of(2025, 1, 15, 17, 0))
        val shiftRequest = ShiftRequest(
            id = 1L,
            shift = shift,
            requesterId = shift.userId,
            targetUserId = 200L,
            status = RequestStatus.TARGET_APPROVED
        )
        every { shiftRequestRepository.findById(1L) } returns Optional.of(shiftRequest)
        every { shiftRequestRepository.save(any()) } answers { firstArg() }

        val result = shiftRequestService.rejectByAdmin(shiftRequest.id)

        assertEquals(RequestStatus.ADMIN_REJECTED, result.status)
        assertEquals(100L, result.shift.userId) // Ownership stays with requester (never transferred)
        assertEquals(100L, result.requesterId)
        assertEquals(200L, result.targetUserId)
    }

    @Test
    fun should_return_filtered_list_when_getting_requests_by_requester() {
        // Given: Multiple requests from the same requester with different targets
        val shift1 = Shift(id = 1L, status = ShiftStatus.APPROVED, userId = 100L, clockInTime = java.time.LocalDateTime.of(2025, 1, 15, 9, 0), clockOutTime = java.time.LocalDateTime.of(2025, 1, 15, 17, 0))
        val shift2 = Shift(id = 2L, status = ShiftStatus.APPROVED, userId = 100L, clockInTime = java.time.LocalDateTime.of(2025, 1, 15, 9, 0), clockOutTime = java.time.LocalDateTime.of(2025, 1, 15, 17, 0))
        val request1 = ShiftRequest(id = 1L, shift = shift1, requesterId = 100L, targetUserId = 200L, status = RequestStatus.PENDING)
        val request2 = ShiftRequest(id = 2L, shift = shift2, requesterId = 100L, targetUserId = 300L, status = RequestStatus.TARGET_APPROVED)

        every { shiftRequestRepository.findAllByRequesterId(100L) } returns listOf(request1, request2)

        // When: Getting requests by requester
        val result = shiftRequestService.getRequestsByRequester(100L)

        // Then: Should return all requests for that requester
        assertEquals(2, result.size)
        assertEquals(100L, result[0].requesterId)
        assertEquals(200L, result[0].targetUserId)
        assertEquals(100L, result[1].requesterId)
        assertEquals(300L, result[1].targetUserId)
        
        // Verify repository was called with correct parameter
        verify(exactly = 1) { shiftRequestRepository.findAllByRequesterId(100L) }
    }

    @Test
    fun should_return_empty_list_when_no_requests_found_for_requester() {
        // Given: No requests for the requester
        every { shiftRequestRepository.findAllByRequesterId(999L) } returns emptyList()

        // When: Getting requests by non-existent requester
        val result = shiftRequestService.getRequestsByRequester(999L)

        // Then: Should return empty list
        assertTrue(result.isEmpty())
        verify(exactly = 1) { shiftRequestRepository.findAllByRequesterId(999L) }
    }

    @Test
    fun should_return_filtered_list_when_getting_requests_by_target_user() {
        // Given: Multiple requests targeting the same user from different requesters
        val shift1 = Shift(id = 1L, status = ShiftStatus.APPROVED, userId = 100L, clockInTime = java.time.LocalDateTime.of(2025, 1, 15, 9, 0), clockOutTime = java.time.LocalDateTime.of(2025, 1, 15, 17, 0))
        val shift2 = Shift(id = 2L, status = ShiftStatus.APPROVED, userId = 300L, clockInTime = java.time.LocalDateTime.of(2025, 1, 15, 9, 0), clockOutTime = java.time.LocalDateTime.of(2025, 1, 15, 17, 0))
        val request1 = ShiftRequest(id = 1L, shift = shift1, requesterId = 100L, targetUserId = 200L, status = RequestStatus.PENDING)
        val request2 = ShiftRequest(id = 2L, shift = shift2, requesterId = 300L, targetUserId = 200L, status = RequestStatus.TARGET_APPROVED)

        every { shiftRequestRepository.findAllByTargetUserId(200L) } returns listOf(request1, request2)

        // When: Getting requests by target user
        val result = shiftRequestService.getRequestsByTargetUser(200L)

        // Then: Should return all requests targeting that user
        assertEquals(2, result.size)
        assertEquals(200L, result[0].targetUserId)
        assertEquals(100L, result[0].requesterId)
        assertEquals(200L, result[1].targetUserId)
        assertEquals(300L, result[1].requesterId)
        
        // Verify repository was called with correct parameter
        verify(exactly = 1) { shiftRequestRepository.findAllByTargetUserId(200L) }
    }

    @Test
    fun should_return_empty_list_when_no_requests_found_for_target_user() {
        // Given: No requests for the target user
        every { shiftRequestRepository.findAllByTargetUserId(888L) } returns emptyList()

        // When: Getting requests by non-existent target user
        val result = shiftRequestService.getRequestsByTargetUser(888L)

        // Then: Should return empty list
        assertTrue(result.isEmpty())
        verify(exactly = 1) { shiftRequestRepository.findAllByTargetUserId(888L) }
    }

    @Test
    fun should_return_filtered_list_when_getting_requests_by_status() {
        // Given: Multiple requests with different statuses
        val shift1 = Shift(id = 1L, status = ShiftStatus.APPROVED, userId = 100L, clockInTime = java.time.LocalDateTime.of(2025, 1, 15, 9, 0), clockOutTime = java.time.LocalDateTime.of(2025, 1, 15, 17, 0))
        val shift2 = Shift(id = 2L, status = ShiftStatus.APPROVED, userId = 300L, clockInTime = java.time.LocalDateTime.of(2025, 1, 15, 9, 0), clockOutTime = java.time.LocalDateTime.of(2025, 1, 15, 17, 0))
        val shift3 = Shift(id = 3L, status = ShiftStatus.APPROVED, userId = 400L, clockInTime = java.time.LocalDateTime.of(2025, 1, 15, 9, 0), clockOutTime = java.time.LocalDateTime.of(2025, 1, 15, 17, 0))
        val request1 = ShiftRequest(id = 1L, shift = shift1, requesterId = 100L, targetUserId = 200L, status = RequestStatus.PENDING)
        val request2 = ShiftRequest(id = 2L, shift = shift2, requesterId = 300L, targetUserId = 200L, status = RequestStatus.TARGET_APPROVED)
        val request3 = ShiftRequest(id = 3L, shift = shift3, requesterId = 400L, targetUserId = 500L, status = RequestStatus.PENDING)

        every { shiftRequestRepository.findAllByStatus(RequestStatus.PENDING) } returns listOf(request1, request3)
        every { shiftRequestRepository.findAllByStatus(RequestStatus.TARGET_APPROVED) } returns listOf(request2)

        // When: Getting requests by different statuses
        val pendingResults = shiftRequestService.getRequestsByStatus(RequestStatus.PENDING)
        val approvedResults = shiftRequestService.getRequestsByStatus(RequestStatus.TARGET_APPROVED)

        // Then: Should return correctly filtered results
        assertEquals(2, pendingResults.size)
        assertEquals(RequestStatus.PENDING, pendingResults[0].status)
        assertEquals(RequestStatus.PENDING, pendingResults[1].status)

        assertEquals(1, approvedResults.size)
        assertEquals(RequestStatus.TARGET_APPROVED, approvedResults[0].status)
        
        // Verify repository was called with correct parameters
        verify(exactly = 1) { shiftRequestRepository.findAllByStatus(RequestStatus.PENDING) }
        verify(exactly = 1) { shiftRequestRepository.findAllByStatus(RequestStatus.TARGET_APPROVED) }
    }

    @Test
    fun should_return_empty_list_when_no_requests_found_for_status() {
        // Given: No requests with the specified status
        every { shiftRequestRepository.findAllByStatus(RequestStatus.ADMIN_REJECTED) } returns emptyList()

        // When: Getting requests by status with no matches
        val result = shiftRequestService.getRequestsByStatus(RequestStatus.ADMIN_REJECTED)

        // Then: Should return empty list
        assertTrue(result.isEmpty())
        verify(exactly = 1) { shiftRequestRepository.findAllByStatus(RequestStatus.ADMIN_REJECTED) }
    }
    
    @Test
    fun should_throw_exception_when_request_is_not_found() {
        // Given: Request does not exist
        every { shiftRequestRepository.findById(999L) } returns Optional.empty()

        // When/Then: All approval/rejection methods should throw exception
        val exception1 = assertThrows<IllegalArgumentException> {
            shiftRequestService.approveByTargetUser(999L)
        }
        assertEquals("Request not found", exception1.message)

        val exception2 = assertThrows<IllegalArgumentException> {
            shiftRequestService.approveByAdmin(999L)
        }
        assertEquals("Request not found", exception2.message)

        val exception3 = assertThrows<IllegalArgumentException> {
            shiftRequestService.rejectByTargetUser(999L)
        }
        assertEquals("Request not found", exception3.message)

        val exception4 = assertThrows<IllegalArgumentException> {
            shiftRequestService.rejectByAdmin(999L)
        }
        assertEquals("Request not found", exception4.message)

        // Verify repository was called 4 times (once for each method)
        verify(exactly = 4) { shiftRequestRepository.findById(999L) }
    }

    @Test
    fun should_throw_exception_when_shift_is_not_found_during_request_creation() {
        // Given: Shift does not exist
        every { shiftRepository.findById(777L) } returns Optional.empty()

        // When/Then: Should throw exception when creating request with non-existent shift
        val exception = assertThrows<IllegalArgumentException> {
            shiftRequestService.createRequest(requesterId = 100L, shiftId = 777L, targetUserId = 200L)
        }
        
        assertEquals("Shift not found", exception.message)
        verify(exactly = 1) { shiftRepository.findById(777L) }
    }


}
