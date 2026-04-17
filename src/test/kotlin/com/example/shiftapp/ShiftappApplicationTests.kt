package com.example.shiftapp

import org.junit.jupiter.api.Test
import kotlin.test.assertTrue

/**
 * Placeholder sanity test for the TDD phase.
 *
 * Intentionally NOT using @SpringBootTest — we do not want to bootstrap the
 * full application context while we are building and testing the service
 * layer in isolation. This keeps the test cycle fast and avoids requiring
 * a database, security, or web stack.
 *
 * Once controllers / persistence are introduced, a proper @SpringBootTest
 * (or sliced @DataJpaTest / @WebMvcTest) can be reintroduced.
 */
class ShiftappApplicationTests {

	@Test
	fun `jvm test harness works`() {
		assertTrue(true)
	}
}
