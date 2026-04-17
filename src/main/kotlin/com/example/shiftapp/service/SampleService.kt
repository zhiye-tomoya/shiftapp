package com.example.shiftapp.service

import org.springframework.stereotype.Service

/**
 * Placeholder service to demonstrate the TDD setup.
 *
 * Real services for the shift-app domain (e.g. ShiftService, EmployeeService)
 * should be added here and driven by unit tests under
 * `src/test/kotlin/com/example/shiftapp/service/`.
 *
 * Note: @Service is present so the class behaves like a normal Spring
 * component, but unit tests instantiate it directly — no Spring context
 * is required.
 */
@Service
class SampleService {

    /**
     * Trivial example of domain-style logic that can be TDD'd without any
     * infrastructure (DB, web, security).
     */
    fun greet(name: String): String {
        require(name.isNotBlank()) { "name must not be blank" }
        return "Hello, $name!"
    }
}
