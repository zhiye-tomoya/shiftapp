package com.example.shiftapp.exception

import com.example.shiftapp.dto.response.ErrorResponse
import org.springframework.dao.OptimisticLockingFailureException
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.access.AccessDeniedException
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
     * Handle Spring Security's [AccessDeniedException].
     *
     * Status: 403 Forbidden
     *
     * Triggered both by `@PreAuthorize` failures (e.g. STAFF hitting an
     * ADMIN-only endpoint) and by service-layer permission checks (e.g.
     * STAFF trying to edit someone else's shift, or to edit a non-DRAFT
     * shift). 403 is the correct HTTP semantic — "the server understood
     * the request but refuses to authorize it" — distinct from 401
     * (no/invalid auth) and 500 (server bug).
     *
     * This handler must be more specific than [handleGeneralException]
     * for `@ExceptionHandler` resolution to pick it.
     */
    @ExceptionHandler(AccessDeniedException::class)
    fun handleAccessDeniedException(
        ex: AccessDeniedException,
        request: WebRequest,
    ): ResponseEntity<ErrorResponse> {
        val error = ErrorResponse(
            status = HttpStatus.FORBIDDEN.value(),
            error = "Forbidden",
            message = ex.message ?: "Access is denied",
            path = request.getDescription(false).substringAfter("uri="),
        )
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(error)
    }

    /**
     * Handle JPA's [OptimisticLockingFailureException].
     *
     * Status: 409 Conflict
     *
     * Two situations land here:
     *  1. Service-level pre-check: the caller sent a `version` in
     *     `PUT/PATCH /api/shifts/{id}` that no longer matches the DB.
     *  2. Backstop at flush: even when the caller skipped the version
     *     field, Hibernate's `@Version` column will trip if a concurrent
     *     transaction won the race.
     *
     * In both cases, the right answer for the client is the same: re-read
     * the current shift, re-apply your edit, and retry.
     */
    @ExceptionHandler(OptimisticLockingFailureException::class)
    fun handleOptimisticLockingFailureException(
        ex: OptimisticLockingFailureException,
        request: WebRequest,
    ): ResponseEntity<ErrorResponse> {
        val error = ErrorResponse(
            status = HttpStatus.CONFLICT.value(),
            error = "Conflict",
            message = ex.message ?: "Resource was modified by another transaction",
            path = request.getDescription(false).substringAfter("uri="),
        )
        return ResponseEntity.status(HttpStatus.CONFLICT).body(error)
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

