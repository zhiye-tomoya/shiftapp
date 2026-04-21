package com.example.shiftapp.service

import com.example.shiftapp.domain.Shift
import com.example.shiftapp.domain.ShiftRequest
import com.example.shiftapp.domain.RequestStatus
import com.example.shiftapp.domain.ShiftStatus
import com.example.shiftapp.repository.ShiftRepository
import com.example.shiftapp.repository.ShiftRequestRepository
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import kotlin.test.assertEquals

class ShiftRequestServiceTest {

    private val shiftRepository: ShiftRepository = mockk()
    private val shiftRequestRepository: ShiftRequestRepository = mockk()
    private val shiftRequestService = ShiftRequestService(shiftRepository, shiftRequestRepository)

    @Test
    fun should_create_and_save_new_request_when_create_request_is_called() {
        val shift = Shift(id = 1L, status = ShiftStatus.APPROVED, userId = 100L)
        every { shiftRepository.findById(1L) } returns shift
        every { shiftRequestRepository.save(any()) } answers { firstArg() }

        val result = shiftRequestService.createRequest(requesterId = shift.userId, shiftId = 1L, targetUserId = 300L)

        assertEquals(RequestStatus.PENDING, result.status)
        assertEquals(shift.userId, result.requesterId)
        assertEquals(1L, result.shift.id)
        assertEquals(300L, result.targetUserId)
    }

    @Test
    fun should_retrieve_approve_and_save_request_when_approved_by_target_user() {
        val shift = Shift(id = 1L, status = ShiftStatus.APPROVED, userId = 100L)
        val shiftRequest = ShiftRequest(
            id = 1L,
            shift = shift,
            requesterId = shift.userId,
            targetUserId = 200L,
            status = RequestStatus.PENDING
        )
        every { shiftRequestRepository.findById(1L) } returns shiftRequest
        every { shiftRequestRepository.save(any()) } answers { firstArg() }

        val result = shiftRequestService.approveByTargetUser(shiftRequest.id)

        assertEquals(RequestStatus.TARGET_APPROVED, result.status)
        assertEquals(100L, result.shift.userId) // Shift ownership stays with requester (not transferred yet)
        assertEquals(100L, result.requesterId) // Requester remains unchanged
        assertEquals(200L, result.targetUserId) // Target user remains unchanged
    }
    @Test
    fun should_retrieve_reject_and_save_request_when_rejected_by_target_user() {
        val shift = Shift(id = 1L, status = ShiftStatus.APPROVED, userId = 100L)
        val shiftRequest = ShiftRequest(
            id =1L,
            shift = shift,
            requesterId = shift.userId,
            targetUserId = 200L,
            status = RequestStatus.PENDING
        )
        every { shiftRequestRepository.findById(1L) } returns shiftRequest
        every { shiftRequestRepository.save(any()) } answers { firstArg() }

        val result = shiftRequestService.rejectByTargetUser(shiftRequest.id)

        assertEquals(RequestStatus.TARGET_REJECTED, result.status)
        assertEquals(100L, result.shift.userId) // Ownership stays with requester
        assertEquals(100L, result.requesterId) 
        assertEquals(200L, result.targetUserId) 
    }
    @Test
    fun should_retrieve_approve_and_save_request_when_approved_by_admin() {
        val shift = Shift(id = 1L, status = ShiftStatus.APPROVED, userId = 100L)
        val shiftRequest = ShiftRequest(
            id = 1L,
            shift = shift,
            requesterId = shift.userId,
            targetUserId = 200L,
            status = RequestStatus.TARGET_APPROVED
        )
        every { shiftRequestRepository.findById(1L) } returns shiftRequest
        every { shiftRequestRepository.save(any()) } answers { firstArg() } 

        val result = shiftRequestService.approveByAdmin(shiftRequest.id)

        assertEquals(RequestStatus.ADMIN_APPROVED, result.status) 
        assertEquals(200L, result.shift.userId) // Ownership NOW transfers to target user on admin approval
        assertEquals(100L, result.requesterId) 
        assertEquals(200L, result.targetUserId)
    }
    @Test
    fun should_retrieve_reject_and_save_request_when_rejected_by_admin() {
        val shift = Shift(id = 1L, status = ShiftStatus.APPROVED, userId = 100L)
        val shiftRequest = ShiftRequest(
            id = 1L,
            shift = shift,
            requesterId = shift.userId,
            targetUserId = 200L,
            status = RequestStatus.TARGET_APPROVED
        )
        every { shiftRequestRepository.findById(1L) } returns shiftRequest
        every { shiftRequestRepository.save(any()) } answers { firstArg() }

        val result = shiftRequestService.rejectByAdmin(shiftRequest.id)

        assertEquals(RequestStatus.ADMIN_REJECTED, result.status)
        assertEquals(100L, result.shift.userId) // Ownership stays with requester (never transferred)
        assertEquals(100L, result.requesterId)
        assertEquals(200L, result.targetUserId)
    }
    @Test
    fun should_return_filtered_list_when_getting_requests_by_requester() {
        // Test implementation here
    }
    @Test
    fun should_return_filtered_list_when_getting_requests_by_target_user() {
        // Test implementation here
    }
    @Test
    fun should_return_filtered_list_when_getting_requests_by_status() {
        // Test implementation here
    }
    @Test
    fun should_throw_exception_when_request_is_not_found() {
        // Test implementation here
    }


}