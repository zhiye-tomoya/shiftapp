package com.example.shiftapp.exception

import com.example.shiftapp.dto.response.ErrorResponse
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import org.springframework.web.context.request.WebRequest

/**
 * Global exception handler for all REST controllers.
 *
 * This catches exceptions thrown anywhere in the application and converts them
 * to consistent error responses. Clients always get the same error format!
 *
 * Benefits:
 * - Consistent error format across all endpoints
 * - Clean controller code (no try-catch blocks needed)
 * - Easy to add new exception types
 *
 * Error format: { timestamp, status, error, message, path }
 */
@RestControllerAdvice
class GlobalExceptionHandler {

    /**
     * Handle IllegalArgumentException.
     *
     * Used for: Bad request data (invalid email, etc.)
     * Status: 400 Bad Request
     *
     * Example: "Email already exists", "Invalid role"
     */
    @ExceptionHandler(IllegalArgumentException::class)
    fun handleIllegalArgumentException(
        ex: IllegalArgumentException,
        request: WebRequest
    ): ResponseEntity<ErrorResponse> {
        val error = ErrorResponse(
            status = HttpStatus.BAD_REQUEST.value(),
            error = "Bad Request",
            message = ex.message ?: "Invalid argument",
            path = request.getDescription(false).substringAfter("uri=")
        )
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(error)
    }

    /**
     * Handle IllegalStateException.
     *
     * Used for: Invalid state transitions, business rule violations
     * Status: 409 Conflict
     *
     * Example: "Only DRAFT shifts can be submitted", "Shift not found"
     */
    @ExceptionHandler(IllegalStateException::class)
    fun handleIllegalStateException(
        ex: IllegalStateException,
        request: WebRequest
    ): ResponseEntity<ErrorResponse> {
        val error = ErrorResponse(
            status = HttpStatus.CONFLICT.value(),
            error = "Conflict",
            message = ex.message ?: "Invalid state",
            path = request.getDescription(false).substringAfter("uri=")
        )
        return ResponseEntity.status(HttpStatus.CONFLICT).body(error)
    }

    /**
     * Handle validation errors from @Valid annotation.
     *
     * Status: 400 Bad Request
     *
     * Example: "password: Password must be at least 8 characters"
     *
     * This catches validation failures on request DTOs.
     */
    @ExceptionHandler(MethodArgumentNotValidException::class)
    fun handleValidationException(
        ex: MethodArgumentNotValidException,
        request: WebRequest
    ): ResponseEntity<ErrorResponse> {
        // Combine all validation errors into one message
        val message = ex.bindingResult.fieldErrors
            .joinToString(", ") { "${it.field}: ${it.defaultMessage}" }

        val error = ErrorResponse(
            status = HttpStatus.BAD_REQUEST.value(),
            error = "Validation Failed",
            message = message,
            path = request.getDescription(false).substringAfter("uri=")
        )
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(error)
    }

    /**
     * Handle all other exceptions (catch-all).
     *
     * Status: 500 Internal Server Error
     *
     * This catches unexpected errors that we didn't anticipate.
     * In production, you'd log these for investigation!
     */
    @ExceptionHandler(Exception::class)
    fun handleGeneralException(
        ex: Exception,
        request: WebRequest
    ): ResponseEntity<ErrorResponse> {
        val error = ErrorResponse(
            status = HttpStatus.INTERNAL_SERVER_ERROR.value(),
            error = "Internal Server Error",
            message = ex.message ?: "An unexpected error occurred",
            path = request.getDescription(false).substringAfter("uri=")
        )
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error)
    }
}
