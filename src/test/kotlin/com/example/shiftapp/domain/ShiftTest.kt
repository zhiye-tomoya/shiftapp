package com.example.shiftapp.domain

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import kotlin.test.assertEquals

class ShiftTest {

    @Test
    fun `new shift should start as DRAFT`() {
        val shift = Shift(id = 1L, status = ShiftStatus.DRAFT, userId = 100L)
        assertEquals(ShiftStatus.DRAFT, shift.status)
    }

    @Test
    fun `should change status to SUBMITTED when submit is called on DRAFT shift`() {
        val shift = Shift(id = 1L, status = ShiftStatus.DRAFT, userId = 100L)
        val submittedShift = shift.submit()
        assertEquals(ShiftStatus.SUBMITTED, submittedShift.status)
    }

    @Test
    fun `should throw exception when submit is called on non-DRAFT shift`() {
        val shift = Shift(id = 1L, status = ShiftStatus.SUBMITTED, userId = 100L)
        assertThrows<IllegalStateException> {
            shift.submit()
        }
    }

    @Test
    fun `should change status to APPROVED when approve is called on SUBMITTED shift`() {
        val shift = Shift(id = 1L, status = ShiftStatus.SUBMITTED, userId = 100L)
        val approvedShift = shift.approve()
        assertEquals(ShiftStatus.APPROVED, approvedShift.status)
    }

    @Test
    fun `should throw exception when approve is called on non-SUBMITTED shift`() {
        val shift = Shift(id = 1L, status = ShiftStatus.APPROVED, userId = 100L)
        assertThrows<IllegalStateException> {
            shift.approve()
        }
    }

    @Test
    fun `should change status to REJECTED when reject is called on SUBMITTED shift`() {
        val shift = Shift(id = 1L, status = ShiftStatus.SUBMITTED, userId = 100L)
        val rejectedShift = shift.reject()
        assertEquals(ShiftStatus.REJECTED, rejectedShift.status)
    }

    @Test
    fun `should throw exception when reject is called on non-SUBMITTED shift`() {
        val shift = Shift(id = 1L, status = ShiftStatus.APPROVED, userId = 100L)
        assertThrows<IllegalStateException> {
            shift.reject()
        }
    }

    @Test
    fun `should throw exception when submit is called on APPROVED shift`() {
        val shift = Shift(id = 1L, status = ShiftStatus.APPROVED, userId = 100L)
        assertThrows<IllegalStateException> {
            shift.submit()
        }
    }

    @Test
    fun `should throw exception when approve is called on REJECTED shift`() {
        val shift = Shift(id = 1L, status = ShiftStatus.REJECTED, userId = 100L)
        assertThrows<IllegalStateException> {
            shift.approve()
        }
    }

    @Test
    fun `submit should not mutate original shift`() {
        val original = Shift(id = 1L, status = ShiftStatus.DRAFT, userId = 100L)
        val submitted = original.submit()
        assertEquals(ShiftStatus.DRAFT, original.status)
        assertEquals(ShiftStatus.SUBMITTED, submitted.status)
    }

    @Test
    fun `approve should not mutate original shift`() {
        val original = Shift(id = 1L, status = ShiftStatus.SUBMITTED, userId = 100L)
        val approved = original.approve()
        assertEquals(ShiftStatus.SUBMITTED, original.status)
        assertEquals(ShiftStatus.APPROVED, approved.status)
    }

    @Test
    fun `reject should not mutate original shift`() {
        val original = Shift(id = 1L, status = ShiftStatus.SUBMITTED, userId = 100L)
        val rejected = original.reject()
        assertEquals(ShiftStatus.SUBMITTED, original.status)
        assertEquals(ShiftStatus.REJECTED, rejected.status)
    }
}
