package com.example.shiftapp.domain

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import kotlin.test.assertEquals

/**
 * TDD tests for ShiftRequest domain model.
 * 
 * These tests drive the design of the ShiftRequest domain model,
 * ensuring all business rules are enforced within the domain itself.
 */
class ShiftRequestTest {

    @Test
    fun `new request should start as PENDING`() {
        val shift = Shift(id = 1L, status = ShiftStatus.APPROVED, userId = 100L)
        val request = ShiftRequest(
            id = 1L,
            shift = shift,
            requesterId = 100L,
            targetUserId = 200L,
            status = RequestStatus.PENDING
        )

        assertEquals(RequestStatus.PENDING, request.status)
    }

    @Test
    fun `target user can approve a PENDING request`() {
        val shift = Shift(id = 1L, status = ShiftStatus.APPROVED, userId = 100L)
        val request = ShiftRequest(
            id = 1L,
            shift = shift,
            requesterId = 100L,
            targetUserId = 200L,
            status = RequestStatus.PENDING
        )

        val approvedRequest = request.approveByTargetUser()

        assertEquals(RequestStatus.TARGET_APPROVED, approvedRequest.status)
        assertEquals(200L, approvedRequest.shift.userId, "Shift ownership should be transferred to target user")
    }

    @Test
    fun `target user can reject a PENDING request`() {
        val shift = Shift(id = 1L, status = ShiftStatus.APPROVED, userId = 100L)
        val request = ShiftRequest(
            id = 1L,
            shift = shift,
            requesterId = 100L,
            targetUserId = 200L,
            status = RequestStatus.PENDING
        )

        val rejectedRequest = request.rejectByTargetUser()

        assertEquals(RequestStatus.REJECTED, rejectedRequest.status)
        assertEquals(100L, rejectedRequest.shift.userId, "Shift ownership should remain with requester")
    }

    @Test
    fun `cannot approve an already TARGET_APPROVED request`() {
        val shift = Shift(id = 1L, status = ShiftStatus.APPROVED, userId = 200L)
        val request = ShiftRequest(
            id = 1L,
            shift = shift,
            requesterId = 100L,
            targetUserId = 200L,
            status = RequestStatus.TARGET_APPROVED
        )

        val exception = assertThrows<IllegalStateException> {
            request.approveByTargetUser()
        }

        assertEquals("Only PENDING requests can be approved (was TARGET_APPROVED)", exception.message)
    }

    @Test
    fun `cannot approve an already REJECTED request`() {
        val shift = Shift(id = 1L, status = ShiftStatus.APPROVED, userId = 100L)
        val request = ShiftRequest(
            id = 1L,
            shift = shift,
            requesterId = 100L,
            targetUserId = 200L,
            status = RequestStatus.REJECTED
        )

        val exception = assertThrows<IllegalStateException> {
            request.approveByTargetUser()
        }

        assertEquals("Only PENDING requests can be approved (was REJECTED)", exception.message)
    }

    @Test
    fun `cannot reject an already TARGET_APPROVED request`() {
        val shift = Shift(id = 1L, status = ShiftStatus.APPROVED, userId = 200L)
        val request = ShiftRequest(
            id = 1L,
            shift = shift,
            requesterId = 100L,
            targetUserId = 200L,
            status = RequestStatus.TARGET_APPROVED
        )

        val exception = assertThrows<IllegalStateException> {
            request.rejectByTargetUser()
        }

        assertEquals("Only PENDING requests can be rejected (was TARGET_APPROVED)", exception.message)
    }

    @Test
    fun `cannot reject an already REJECTED request`() {
        val shift = Shift(id = 1L, status = ShiftStatus.APPROVED, userId = 100L)
        val request = ShiftRequest(
            id = 1L,
            shift = shift,
            requesterId = 100L,
            targetUserId = 200L,
            status = RequestStatus.REJECTED
        )

        val exception = assertThrows<IllegalStateException> {
            request.rejectByTargetUser()
        }

        assertEquals("Only PENDING requests can be rejected (was REJECTED)", exception.message)
    }

    @Test
    fun `approved request transfers shift ownership to target user`() {
        val originalShift = Shift(id = 1L, status = ShiftStatus.APPROVED, userId = 100L)
        val request = ShiftRequest(
            id = 1L,
            shift = originalShift,
            requesterId = 100L,
            targetUserId = 200L,
            status = RequestStatus.PENDING
        )

        val approvedRequest = request.approveByTargetUser()

        assertEquals(200L, approvedRequest.shift.userId)
        assertEquals(100L, originalShift.userId, "Original shift should remain unchanged (immutability)")
    }

    @Test
    fun `rejected request keeps shift ownership with requester`() {
        val originalShift = Shift(id = 1L, status = ShiftStatus.APPROVED, userId = 100L)
        val request = ShiftRequest(
            id = 1L,
            shift = originalShift,
            requesterId = 100L,
            targetUserId = 200L,
            status = RequestStatus.PENDING
        )

        val rejectedRequest = request.rejectByTargetUser()

        assertEquals(100L, rejectedRequest.shift.userId)
    }

    // ===== 2-Step Approval: Admin Approval Tests =====

    @Test
    fun `admin can approve a TARGET_APPROVED request`() {
        val shift = Shift(id = 1L, status = ShiftStatus.APPROVED, userId = 200L)
        val request = ShiftRequest(
            id = 1L,
            shift = shift,
            requesterId = 100L,
            targetUserId = 200L,
            status = RequestStatus.TARGET_APPROVED
        )

        val adminApprovedRequest = request.approveByAdmin()

        assertEquals(RequestStatus.ADMIN_APPROVED, adminApprovedRequest.status)
        assertEquals(200L, adminApprovedRequest.shift.userId, "Shift ownership should remain with target user")
    }

    @Test
    fun `admin cannot approve a PENDING request`() {
        val shift = Shift(id = 1L, status = ShiftStatus.APPROVED, userId = 100L)
        val request = ShiftRequest(
            id = 1L,
            shift = shift,
            requesterId = 100L,
            targetUserId = 200L,
            status = RequestStatus.PENDING
        )

        val exception = assertThrows<IllegalStateException> {
            request.approveByAdmin()
        }

        assertEquals("Only TARGET_APPROVED requests can be admin-approved (was PENDING)", exception.message)
    }

    @Test
    fun `admin can reject a TARGET_APPROVED request`() {
        val shift = Shift(id = 1L, status = ShiftStatus.APPROVED, userId = 200L)
        val request = ShiftRequest(
            id = 1L,
            shift = shift,
            requesterId = 100L,
            targetUserId = 200L,
            status = RequestStatus.TARGET_APPROVED
        )

        val rejectedRequest = request.rejectByAdmin()

        assertEquals(RequestStatus.REJECTED, rejectedRequest.status)
    }

    @Test
    fun `admin cannot reject a PENDING request`() {
        val shift = Shift(id = 1L, status = ShiftStatus.APPROVED, userId = 100L)
        val request = ShiftRequest(
            id = 1L,
            shift = shift,
            requesterId = 100L,
            targetUserId = 200L,
            status = RequestStatus.PENDING
        )

        val exception = assertThrows<IllegalStateException> {
            request.rejectByAdmin()
        }

        assertEquals("Only TARGET_APPROVED requests can be admin-rejected (was PENDING)", exception.message)
    }
}
