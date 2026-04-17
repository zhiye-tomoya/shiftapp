package com.example.shiftapp.service

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import kotlin.test.assertEquals

/**
 * Pure unit test for [SampleService].
 *
 * - No @SpringBootTest, no @ExtendWith(SpringExtension::class).
 * - The service is instantiated directly, so tests run in milliseconds.
 * - Collaborators, when they exist, should be mocked with MockK.
 */
class SampleServiceTest {

    private val service = SampleService()

    @Test
    fun `greet returns a greeting for a valid name`() {
        val result = service.greet("Tomoya")

        assertEquals("Hello, Tomoya!", result)
    }

    @Test
    fun `greet rejects blank name`() {
        assertThrows<IllegalArgumentException> {
            service.greet("   ")
        }
    }
}
