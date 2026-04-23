# Phase 4: REST API Layer

## Overview

Phase 4 focuses on **exposing the application through REST APIs**. We'll build controllers, add security with Spring Security + JWT, implement role-based authorization (ADMIN vs STAFF), and add API documentation.

**Status:** 🟡 Not Started

---

## Goals

✅ **Create DTOs (Data Transfer Objects)**
- Separate domain models from API contracts
- Add validation annotations
- Map between domain and DTOs

✅ **Build REST Controllers**
- Expose HTTP endpoints for shifts and swap requests
- Follow RESTful conventions
- Return proper HTTP status codes

✅ **Add Spring Security + JWT Authentication**
- Secure endpoints with JWT tokens
- Implement authentication filter
- Add login/register endpoints

✅ **Implement Role-Based Authorization**
- ADMIN: Can approve/reject requests, manage all shifts
- STAFF: Can create shifts, create swap requests, approve own requests

✅ **Add API Documentation**
- Integrate Swagger/OpenAPI
- Document all endpoints with examples
- Generate interactive API docs

✅ **Create Controller Integration Tests**
- Test endpoints with MockMvc
- Test security rules
- Test error handling

❌ **No production deployment setup** (Phase 5)
❌ **No database migrations** (Phase 5)

---

## What Already Exists

### ✅ Completed (Phases 1-3)

**Domain Models:**
- `Shift` - with JPA annotations
- `ShiftRequest` - with JPA annotations
- `ShiftStatus` and `RequestStatus` enums

**Service Layer:**
- `ShiftService` - orchestrates Shift operations
- `ShiftRequestService` - orchestrates ShiftRequest operations

**Repository Layer:**
- `ShiftRepository` - Spring Data JPA
- `ShiftRequestRepository` - Spring Data JPA

**Infrastructure:**
- PostgreSQL database running
- Embedded Tomcat server (from spring-boot-starter-web)

---

## Tasks for Phase 4

### Task 1: Update build.gradle.kts Dependencies

**File:** `build.gradle.kts`

**Purpose:** Add Spring Security, JWT, and Swagger dependencies

**Add to dependencies block:**

```kotlin
dependencies {
    // ... existing dependencies ...

    // 🆕 Phase 4: Security
    implementation("org.springframework.boot:spring-boot-starter-security")
    implementation("io.jsonwebtoken:jjwt-api:0.12.3")
    runtimeOnly("io.jsonwebtoken:jjwt-impl:0.12.3")
    runtimeOnly("io.jsonwebtoken:jjwt-jackson:0.12.3")

    // 🆕 Phase 4: API Documentation
    implementation("org.springdoc:springdoc-openapi-starter-webmvc-ui:2.3.0")

    // 🆕 Phase 4: Testing
    testImplementation("org.springframework.security:spring-security-test")
}
```

**Why these libraries?**
- `spring-boot-starter-security`: Spring Security framework
- `jjwt`: JWT token generation and validation
- `springdoc-openapi`: Swagger/OpenAPI 3 documentation
- `spring-security-test`: Security testing utilities

---

### Task 2: Create DTOs Package Structure

**Purpose:** Define API contracts separate from domain models

**File structure:**

```
src/main/kotlin/com/example/shiftapp/
└── dto/
    ├── request/
    │   ├── CreateShiftRequest.kt
    │   ├── CreateSwapRequestRequest.kt
    │   ├── LoginRequest.kt
    │   └── RegisterRequest.kt
    └── response/
        ├── ShiftResponse.kt
        ├── ShiftRequestResponse.kt
        ├── AuthResponse.kt
        └── ErrorResponse.kt
```

**Why separate DTOs from domain models?**
- ✅ API versioning flexibility
- ✅ Hide internal implementation details
- ✅ Add validation annotations without polluting domain
- ✅ Control what gets exposed to clients

---

### Task 3: Create Request DTOs

**File:** `src/main/kotlin/com/example/shiftapp/dto/request/CreateShiftRequest.kt`

```kotlin
package com.example.shiftapp.dto.request

import jakarta.validation.constraints.NotNull

/**
 * Request DTO for creating a new shift.
 */
data class CreateShiftRequest(
    @field:NotNull(message = "User ID is required")
    val userId: Long
)
```

---

**File:** `src/main/kotlin/com/example/shiftapp/dto/request/CreateSwapRequestRequest.kt`

```kotlin
package com.example.shiftapp.dto.request

import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Positive

/**
 * Request DTO for creating a new shift swap request.
 */
data class CreateSwapRequestRequest(
    @field:NotNull(message = "Shift ID is required")
    @field:Positive(message = "Shift ID must be positive")
    val shiftId: Long,

    @field:NotNull(message = "Target user ID is required")
    @field:Positive(message = "Target user ID must be positive")
    val targetUserId: Long
)
```

---

**File:** `src/main/kotlin/com/example/shiftapp/dto/request/LoginRequest.kt`

```kotlin
package com.example.shiftapp.dto.request

import jakarta.validation.constraints.NotBlank

/**
 * Request DTO for user login.
 */
data class LoginRequest(
    @field:NotBlank(message = "Email is required")
    val email: String,

    @field:NotBlank(message = "Password is required")
    val password: String
)
```

---

**File:** `src/main/kotlin/com/example/shiftapp/dto/request/RegisterRequest.kt`

```kotlin
package com.example.shiftapp.dto.request

import jakarta.validation.constraints.Email
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size

/**
 * Request DTO for user registration.
 */
data class RegisterRequest(
    @field:NotBlank(message = "Name is required")
    val name: String,

    @field:NotBlank(message = "Email is required")
    @field:Email(message = "Email must be valid")
    val email: String,

    @field:NotBlank(message = "Password is required")
    @field:Size(min = 8, message = "Password must be at least 8 characters")
    val password: String,

    @field:NotBlank(message = "Role is required")
    val role: String  // "ADMIN" or "STAFF"
)
```

---

### Task 4: Create Response DTOs

**File:** `src/main/kotlin/com/example/shiftapp/dto/response/ShiftResponse.kt`

```kotlin
package com.example.shiftapp.dto.response

/**
 * Response DTO for Shift entity.
 */
data class ShiftResponse(
    val id: Long,
    val userId: Long,
    val status: String
)
```

---

**File:** `src/main/kotlin/com/example/shiftapp/dto/response/ShiftRequestResponse.kt`

```kotlin
package com.example.shiftapp.dto.response

/**
 * Response DTO for ShiftRequest entity.
 */
data class ShiftRequestResponse(
    val id: Long,
    val shift: ShiftResponse,
    val requesterId: Long,
    val targetUserId: Long,
    val status: String
)
```

---

**File:** `src/main/kotlin/com/example/shiftapp/dto/response/AuthResponse.kt`

```kotlin
package com.example.shiftapp.dto.response

/**
 * Response DTO for authentication operations.
 */
data class AuthResponse(
    val token: String,
    val userId: Long,
    val email: String,
    val role: String
)
```

---

**File:** `src/main/kotlin/com/example/shiftapp/dto/response/ErrorResponse.kt`

```kotlin
package com.example.shiftapp.dto.response

import java.time.LocalDateTime

/**
 * Standardized error response for API errors.
 */
data class ErrorResponse(
    val timestamp: LocalDateTime = LocalDateTime.now(),
    val status: Int,
    val error: String,
    val message: String,
    val path: String
)
```

---

### Task 5: Create Mapper Utilities

**File:** `src/main/kotlin/com/example/shiftapp/dto/mapper/DtoMappers.kt`

```kotlin
package com.example.shiftapp.dto.mapper

import com.example.shiftapp.domain.Shift
import com.example.shiftapp.domain.ShiftRequest
import com.example.shiftapp.dto.response.ShiftResponse
import com.example.shiftapp.dto.response.ShiftRequestResponse

/**
 * Extension functions to map domain models to DTOs.
 */

fun Shift.toResponse(): ShiftResponse {
    return ShiftResponse(
        id = this.id,
        userId = this.userId,
        status = this.status.name
    )
}

fun ShiftRequest.toResponse(): ShiftRequestResponse {
    return ShiftRequestResponse(
        id = this.id,
        shift = this.shift.toResponse(),
        requesterId = this.requesterId,
        targetUserId = this.targetUserId,
        status = this.status.name
    )
}
```

---

### Task 6: Create User Domain Model and Repository

**File:** `src/main/kotlin/com/example/shiftapp/domain/Role.kt`

```kotlin
package com.example.shiftapp.domain

enum class Role {
    ADMIN,
    STAFF
}
```

---

**File:** `src/main/kotlin/com/example/shiftapp/domain/User.kt`

```kotlin
package com.example.shiftapp.domain

import jakarta.persistence.*

/**
 * User entity representing a system user.
 */
@Entity
@Table(name = "users")
data class User(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0L,

    @Column(nullable = false)
    val name: String,

    @Column(nullable = false, unique = true)
    val email: String,

    @Column(nullable = false)
    val password: String,  // Hashed password

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    val role: Role,

    @Column(name = "store_id", nullable = false)
    val storeId: Long = 1L  // Default store for now
) {
    fun isAdmin(): Boolean = role == Role.ADMIN
    fun isStaff(): Boolean = role == Role.STAFF
}
```

---

**File:** `src/main/kotlin/com/example/shiftapp/repository/UserRepository.kt`

```kotlin
package com.example.shiftapp.repository

import com.example.shiftapp.domain.User
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

@Repository
interface UserRepository : JpaRepository<User, Long> {
    fun findByEmail(email: String): User?
    fun existsByEmail(email: String): Boolean
}
```

---

### Task 7: Create JWT Utility

**File:** `src/main/kotlin/com/example/shiftapp/security/JwtUtil.kt`

```kotlin
package com.example.shiftapp.security

import io.jsonwebtoken.Claims
import io.jsonwebtoken.Jwts
import io.jsonwebtoken.security.Keys
import org.springframework.beans.factory.annotation.Value
import org.springframework.security.core.userdetails.UserDetails
import org.springframework.stereotype.Component
import java.util.*
import javax.crypto.SecretKey

@Component
class JwtUtil {

    @Value("\${jwt.secret}")
    private lateinit var secret: String

    @Value("\${jwt.expiration}")
    private var expiration: Long = 86400000 // 24 hours in milliseconds

    private fun getSigningKey(): SecretKey {
        return Keys.hmacShaKeyFor(secret.toByteArray())
    }

    fun generateToken(email: String, userId: Long, role: String): String {
        val claims = mapOf(
            "userId" to userId,
            "role" to role
        )

        return Jwts.builder()
            .subject(email)
            .claims(claims)
            .issuedAt(Date())
            .expiration(Date(System.currentTimeMillis() + expiration))
            .signWith(getSigningKey())
            .compact()
    }

    fun extractEmail(token: String): String {
        return extractAllClaims(token).subject
    }

    fun extractUserId(token: String): Long {
        return extractAllClaims(token)["userId"] as Int
            .toLong()
    }

    fun extractRole(token: String): String {
        return extractAllClaims(token)["role"] as String
    }

    fun validateToken(token: String, userDetails: UserDetails): Boolean {
        val email = extractEmail(token)
        return email == userDetails.username && !isTokenExpired(token)
    }

    private fun extractAllClaims(token: String): Claims {
        return Jwts.parser()
            .verifyWith(getSigningKey())
            .build()
            .parseSignedClaims(token)
            .payload
    }

    private fun isTokenExpired(token: String): Boolean {
        return extractAllClaims(token).expiration.before(Date())
    }
}
```

---

### Task 8: Update application.properties

**File:** `src/main/resources/application.properties`

**Add JWT configuration:**

```properties
# ===================================================================
# JWT CONFIGURATION
# ===================================================================
jwt.secret=your-super-secret-key-that-should-be-at-least-256-bits-long
jwt.expiration=86400000
```

**Important:** In production, use environment variables for secrets!

---

### Task 9: Create Authentication Service

**File:** `src/main/kotlin/com/example/shiftapp/service/AuthService.kt`

```kotlin
package com.example.shiftapp.service

import com.example.shiftapp.domain.Role
import com.example.shiftapp.domain.User
import com.example.shiftapp.dto.request.LoginRequest
import com.example.shiftapp.dto.request.RegisterRequest
import com.example.shiftapp.dto.response.AuthResponse
import com.example.shiftapp.repository.UserRepository
import com.example.shiftapp.security.JwtUtil
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Service

@Service
class AuthService(
    private val userRepository: UserRepository,
    private val passwordEncoder: PasswordEncoder,
    private val jwtUtil: JwtUtil
) {

    fun register(request: RegisterRequest): AuthResponse {
        // Check if user already exists
        if (userRepository.existsByEmail(request.email)) {
            throw IllegalArgumentException("Email already exists")
        }

        // Validate role
        val role = try {
            Role.valueOf(request.role.uppercase())
        } catch (e: IllegalArgumentException) {
            throw IllegalArgumentException("Invalid role: ${request.role}")
        }

        // Create new user
        val user = User(
            name = request.name,
            email = request.email,
            password = passwordEncoder.encode(request.password),
            role = role
        )

        val savedUser = userRepository.save(user)

        // Generate JWT token
        val token = jwtUtil.generateToken(
            email = savedUser.email,
            userId = savedUser.id,
            role = savedUser.role.name
        )

        return AuthResponse(
            token = token,
            userId = savedUser.id,
            email = savedUser.email,
            role = savedUser.role.name
        )
    }

    fun login(request: LoginRequest): AuthResponse {
        // Find user by email
        val user = userRepository.findByEmail(request.email)
            ?: throw IllegalArgumentException("Invalid email or password")

        // Verify password
        if (!passwordEncoder.matches(request.password, user.password)) {
            throw IllegalArgumentException("Invalid email or password")
        }

        // Generate JWT token
        val token = jwtUtil.generateToken(
            email = user.email,
            userId = user.id,
            role = user.role.name
        )

        return AuthResponse(
            token = token,
            userId = user.id,
            email = user.email,
            role = user.role.name
        )
    }
}
```

---

### Task 10: Create Security Configuration

**File:** `src/main/kotlin/com/example/shiftapp/security/SecurityConfig.kt`

```kotlin
package com.example.shiftapp.security

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity
import org.springframework.security.config.http.SessionCreationPolicy
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.security.web.SecurityFilterChain
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter

@Configuration
@EnableWebSecurity
class SecurityConfig(
    private val jwtAuthenticationFilter: JwtAuthenticationFilter
) {

    @Bean
    fun securityFilterChain(http: HttpSecurity): SecurityFilterChain {
        http
            .csrf { it.disable() }
            .authorizeHttpRequests { auth ->
                auth
                    // Public endpoints
                    .requestMatchers("/api/auth/**").permitAll()
                    .requestMatchers("/swagger-ui/**", "/v3/api-docs/**").permitAll()
                    .requestMatchers("/actuator/health").permitAll()

                    // Admin-only endpoints
                    .requestMatchers("/api/admin/**").hasRole("ADMIN")

                    // Authenticated endpoints
                    .anyRequest().authenticated()
            }
            .sessionManagement { session ->
                session.sessionCreationPolicy(SessionCreationPolicy.STATELESS)
            }
            .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter::class.java)

        return http.build()
    }

    @Bean
    fun passwordEncoder(): PasswordEncoder {
        return BCryptPasswordEncoder()
    }
}
```

---

### Task 11: Create JWT Authentication Filter

**File:** `src/main/kotlin/com/example/shiftapp/security/JwtAuthenticationFilter.kt`

```kotlin
package com.example.shiftapp.security

import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter

@Component
class JwtAuthenticationFilter(
    private val jwtUtil: JwtUtil
) : OncePerRequestFilter() {

    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain
    ) {
        val authHeader = request.getHeader("Authorization")

        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            filterChain.doFilter(request, response)
            return
        }

        try {
            val jwt = authHeader.substring(7)
            val email = jwtUtil.extractEmail(jwt)
            val role = jwtUtil.extractRole(jwt)

            if (SecurityContextHolder.getContext().authentication == null) {
                val authorities = listOf(SimpleGrantedAuthority("ROLE_$role"))

                val authToken = UsernamePasswordAuthenticationToken(
                    email,
                    null,
                    authorities
                )

                authToken.details = WebAuthenticationDetailsSource().buildDetails(request)
                SecurityContextHolder.getContext().authentication = authToken
            }
        } catch (e: Exception) {
            logger.error("JWT validation error: ${e.message}")
        }

        filterChain.doFilter(request, response)
    }
}
```

---

### Task 12: Create Controllers

**File:** `src/main/kotlin/com/example/shiftapp/controller/AuthController.kt`

```kotlin
package com.example.shiftapp.controller

import com.example.shiftapp.dto.request.LoginRequest
import com.example.shiftapp.dto.request.RegisterRequest
import com.example.shiftapp.dto.response.AuthResponse
import com.example.shiftapp.service.AuthService
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/auth")
class AuthController(
    private val authService: AuthService
) {

    @PostMapping("/register")
    fun register(@Valid @RequestBody request: RegisterRequest): ResponseEntity<AuthResponse> {
        val response = authService.register(request)
        return ResponseEntity.status(HttpStatus.CREATED).body(response)
    }

    @PostMapping("/login")
    fun login(@Valid @RequestBody request: LoginRequest): ResponseEntity<AuthResponse> {
        val response = authService.login(request)
        return ResponseEntity.ok(response)
    }
}
```

---

**File:** `src/main/kotlin/com/example/shiftapp/controller/ShiftController.kt`

```kotlin
package com.example.shiftapp.controller

import com.example.shiftapp.domain.Shift
import com.example.shiftapp.domain.ShiftStatus
import com.example.shiftapp.dto.mapper.toResponse
import com.example.shiftapp.dto.request.CreateShiftRequest
import com.example.shiftapp.dto.response.ShiftResponse
import com.example.shiftapp.service.ShiftService
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/shifts")
class ShiftController(
    private val shiftService: ShiftService
) {

    @PostMapping
    fun createShift(@Valid @RequestBody request: CreateShiftRequest): ResponseEntity<ShiftResponse> {
        val shift = Shift(
            userId = request.userId,
            status = ShiftStatus.DRAFT
        )
        val created = shiftService.createShift(shift)
        return ResponseEntity.status(HttpStatus.CREATED).body(created.toResponse())
    }

    @PostMapping("/{id}/submit")
    fun submitShift(@PathVariable id: Long): ResponseEntity<ShiftResponse> {
        val submitted = shiftService.submitShift(id)
        return ResponseEntity.ok(submitted.toResponse())
    }

    @PostMapping("/{id}/approve")
    @PreAuthorize("hasRole('ADMIN')")
    fun approveShift(@PathVariable id: Long): ResponseEntity<ShiftResponse> {
        val approved = shiftService.approveShift(id)
        return ResponseEntity.ok(approved.toResponse())
    }

    @PostMapping("/{id}/reject")
    @PreAuthorize("hasRole('ADMIN')")
    fun rejectShift(@PathVariable id: Long): ResponseEntity<ShiftResponse> {
        val rejected = shiftService.rejectShift(id)
        return ResponseEntity.ok(rejected.toResponse())
    }

    @GetMapping("/{id}")
    fun getShift(@PathVariable id: Long): ResponseEntity<ShiftResponse> {
        val shift = shiftService.getShiftById(id)
        return ResponseEntity.ok(shift.toResponse())
    }

    @GetMapping("/user/{userId}")
    fun getShiftsByUser(@PathVariable userId: Long): ResponseEntity<List<ShiftResponse>> {
        val shifts = shiftService.getShiftsByUserId(userId)
        return ResponseEntity.ok(shifts.map { it.toResponse() })
    }
}
```

---

**File:** `src/main/kotlin/com/example/shiftapp/controller/ShiftRequestController.kt`

```kotlin
package com.example.shiftapp.controller

import com.example.shiftapp.dto.mapper.toResponse
import com.example.shiftapp.dto.request.CreateSwapRequestRequest
import com.example.shiftapp.dto.response.ShiftRequestResponse
import com.example.shiftapp.service.ShiftRequestService
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/requests")
class ShiftRequestController(
    private val shiftRequestService: ShiftRequestService
) {

    @PostMapping
    fun createSwapRequest(
        @Valid @RequestBody request: CreateSwapRequestRequest,
        @RequestHeader("X-User-Id") requesterId: Long  // TODO: Extract from JWT
    ): ResponseEntity<ShiftRequestResponse> {
        val created = shiftRequestService.createRequest(
            requesterId = requesterId,
            shiftId = request.shiftId,
            targetUserId = request.targetUserId
        )
        return ResponseEntity.status(HttpStatus.CREATED).body(created.toResponse())
    }

    @PostMapping("/{id}/approve/target")
    fun approveByTargetUser(@PathVariable id: Long): ResponseEntity<ShiftRequestResponse> {
        val approved = shiftRequestService.approveByTargetUser(id)
        return ResponseEntity.ok(approved.toResponse())
    }

    @PostMapping("/{id}/reject/target")
    fun rejectByTargetUser(@PathVariable id: Long): ResponseEntity<ShiftRequestResponse> {
        val rejected = shiftRequestService.rejectByTargetUser(id)
        return ResponseEntity.ok(rejected.toResponse())
    }

    @PostMapping("/{id}/approve/admin")
    @PreAuthorize("hasRole('ADMIN')")
    fun approveByAdmin(@PathVariable id: Long): ResponseEntity<ShiftRequestResponse> {
        val approved = shiftRequestService.approveByAdmin(id)
        return ResponseEntity.ok(approved.toResponse())
    }

    @PostMapping("/{id}/reject/admin")
    @PreAuthorize("hasRole('ADMIN')")
    fun rejectByAdmin(@PathVariable id: Long): ResponseEntity<ShiftRequestResponse> {
        val rejected = shiftRequestService.rejectByAdmin(id)
        return ResponseEntity.ok(rejected.toResponse())
    }

    @GetMapping("/requester/{requesterId}")
    fun getRequestsByRequester(@PathVariable requesterId: Long): ResponseEntity<List<ShiftRequestResponse>> {
        val requests = shiftRequestService.getRequestsByRequester(requesterId)
        return ResponseEntity.ok(requests.map { it.toResponse() })
    }

    @GetMapping("/target/{targetUserId}")
    fun getRequestsByTargetUser(@PathVariable targetUserId: Long): ResponseEntity<List<ShiftRequestResponse>> {
        val requests = shiftRequestService.getRequestsByTargetUser(targetUserId)
        return ResponseEntity.ok(requests.map { it.toResponse() })
    }
}
```

---

### Task 13: Add Missing Service Methods

**File:** `src/main/kotlin/com/example/shiftapp/service/ShiftService.kt`

**Add these methods:**

```kotlin
fun createShift(shift: Shift): Shift {
    return shiftRepository.save(shift)
}

fun getShiftById(shiftId: Long): Shift {
    return shiftRepository.findById(shiftId)
        .orElseThrow { IllegalStateException("Shift not found: $shiftId") }
}

fun getShiftsByUserId(userId: Long): List<Shift> {
    return shiftRepository.findAllByUserId(userId)
}
```

---

### Task 14: Create Global Exception Handler

**File:** `src/main/kotlin/com/example/shiftapp/exception/GlobalExceptionHandler.kt`

```kotlin
package com.example.shiftapp.exception

import com.example.shiftapp.dto.response.ErrorResponse
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import org.springframework.web.context.request.WebRequest

@RestControllerAdvice
class GlobalExceptionHandler {

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

    @ExceptionHandler(MethodArgumentNotValidException::class)
    fun handleValidationException(
        ex: MethodArgumentNotValidException,
        request: WebRequest
    ): ResponseEntity<ErrorResponse> {
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
```

---

### Task 15: Configure Swagger/OpenAPI

**File:** `src/main/kotlin/com/example/shiftapp/config/OpenApiConfig.kt`

```kotlin
package com.example.shiftapp.config

import io.swagger.v3.oas.models.Components
import io.swagger.v3.oas.models.OpenAPI
import io.swagger.v3.oas.models.info.Info
import io.swagger.v3.oas.models.security.SecurityRequirement
import io.swagger.v3.oas.models.security.SecurityScheme
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class OpenApiConfig {

    @Bean
    fun customOpenAPI(): OpenAPI {
        return OpenAPI()
            .info(
                Info()
                    .title("Shift Swap API")
                    .version("1.0")
                    .description("REST API for managing shift swaps between staff members")
            )
            .addSecurityItem(SecurityRequirement().addList("bearerAuth"))
            .components(
                Components()
                    .addSecuritySchemes(
                        "bearerAuth",
                        SecurityScheme()
                            .type(SecurityScheme.Type.HTTP)
                            .scheme("bearer")
                            .bearerFormat("JWT")
                    )
            )
    }
}
```

**Access Swagger UI at:** `http://localhost:8080/swagger-ui/index.html`

---

### Task 16: Enable Method Security

**File:** `src/main/kotlin/com/example/shiftapp/security/SecurityConfig.kt`

**Add annotation to class:**

```kotlin
@Configuration
@EnableWebSecurity
@EnableMethodSecurity(prePostEnabled = true)  // 🆕 ADD THIS
class SecurityConfig(
    private val jwtAuthenticationFilter: JwtAuthenticationFilter
) {
    // ... rest of the code
}
```

**Import:**

```kotlin
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity
```

---

### Task 17: Create Controller Integration Tests

**File:** `src/test/kotlin/com/example/shiftapp/controller/AuthControllerTest.kt`

```kotlin
package com.example.shiftapp.controller

import com.example.shiftapp.dto.request.RegisterRequest
import com.fasterxml.jackson.databind.ObjectMapper
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.MediaType
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.post

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AuthControllerTest {

    @Autowired
    private lateinit var mockMvc: MockMvc

    @Autowired
    private lateinit var objectMapper: ObjectMapper

    @Test
    fun `should register new user`() {
        val request = RegisterRequest(
            name = "Test User",
            email = "test@example.com",
            password = "password123",
            role = "STAFF"
        )

        mockMvc.post("/api/auth/register") {
            contentType = MediaType.APPLICATION_JSON
            content = objectMapper.writeValueAsString(request)
        }.andExpect {
            status { isCreated() }
            jsonPath("$.token") { exists() }
            jsonPath("$.email") { value("test@example.com") }
            jsonPath("$.role") { value("STAFF") }
        }
    }

    @Test
    fun `should reject duplicate email registration`() {
        val request = RegisterRequest(
            name = "Test User",
            email = "duplicate@example.com",
            password = "password123",
            role = "STAFF"
        )

        // Register first time
        mockMvc.post("/api/auth/register") {
            contentType = MediaType.APPLICATION_JSON
            content = objectMapper.writeValueAsString(request)
        }

        // Register second time (should fail)
        mockMvc.post("/api/auth/register") {
            contentType = MediaType.APPLICATION_JSON
            content = objectMapper.writeValueAsString(request)
        }.andExpect {
            status { isBadRequest() }
        }
    }
}
```

---

## Acceptance Criteria

Phase 4 is complete when:

- [ ] DTOs created for all request/response types
- [ ] User domain model and repository created
- [ ] JWT utility and authentication filter implemented
- [ ] Spring Security configured with role-based access
- [ ] AuthController created (register, login)
- [ ] ShiftController created with proper authorization
- [ ] ShiftRequestController created with proper authorization
- [ ] Global exception handler implemented
- [ ] Swagger/OpenAPI documentation accessible
- [ ] All controller tests pass
- [ ] Can register/login users via API
- [ ] Can create and manage shifts via API
- [ ] Can create and approve swap requests via API
- [ ] Admin-only endpoints properly secured

---

## Testing the API

### 1. Register a STAFF user

```bash
curl -X POST http://localhost:8080/api/auth/register \
  -H "Content-Type: application/json" \
  -d '{
    "name": "John Doe",
    "email": "john@example.com",
    "password": "password123",
    "role": "STAFF"
  }'
```

### 2. Register an ADMIN user

```bash
curl -X POST http://localhost:8080/api/auth/register \
  -H "Content-Type: application/json" \
  -d '{
    "name": "Admin User",
    "email": "admin@example.com",
    "password": "admin123",
    "role": "ADMIN"
  }'
```

### 3. Login

```bash
curl -X POST http://localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{
    "email": "john@example.com",
    "password": "password123"
  }'
```

**Save the token from the response!**

### 4. Create a Shift

```bash
curl -X POST http://localhost:8080/api/shifts \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer YOUR_TOKEN_HERE" \
  -d '{
    "userId": 1
  }'
```

### 5. Submit a Shift

```bash
curl -X POST http://localhost:8080/api/shifts/1/submit \
  -H "Authorization: Bearer YOUR_TOKEN_HERE"
```

### 6. Approve a Shift (Admin only)

```bash
curl -X POST http://localhost:8080/api/shifts/1/approve \
  -H "Authorization: Bearer ADMIN_TOKEN_HERE"
```

---

## What NOT to Do in Phase 4

❌ **Don't add production deployment config**
- No Docker production setup
- No CI/CD pipelines
- → This is Phase 5

❌ **Don't add database migrations**
- Still using `ddl-auto=create-drop`
- Flyway/Liquibase comes in Phase 5

❌ **Don't add frontend**
- API only for now
- Frontend is a separate project

❌ **Don't add complex business features yet**
- Notification system
- Audit logs
- Reporting
- → These come after core API is stable

---

## After Phase 4

Once Phase 4 is complete:

**✅ You'll have:**
- Fully functional REST API
- JWT authentication and authorization
- Role-based access control
- API documentation
- Comprehensive API tests

**➡️ Next (Phase 5):**
- Production database migrations (Flyway)
- Docker production setup
- CI/CD pipeline
- Monitoring and logging
- Performance optimization

---

## Summary

**Phase 4 = REST API + Security**

- Create DTOs to separate API from domain
- Build REST controllers with proper validation
- Add JWT authentication
- Implement role-based authorization (ADMIN/STAFF)
- Add API documentation with Swagger
- Test all endpoints

**Once complete:** You have a production-ready REST API! ✅
**Then move to Phase 5:** Production deployment and operations

🎯 **Let's expose the API to the world!**
