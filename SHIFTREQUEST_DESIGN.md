# ShiftRequest Domain Model - Design Summary

## Overview

A rich domain model implementation of `ShiftRequest` following Domain-Driven Design principles with Test-Driven Development.

## Architecture

### 1. Domain Models Created

#### RequestStatus Enum

```kotlin
enum class RequestStatus {
    PENDING,
    APPROVED,
    REJECTED,
}
```

#### ShiftRequest (Rich Domain Model)

```kotlin
data class ShiftRequest(
    val id: Long,
    val shift: Shift,
    val requesterId: Long,
    val targetUserId: Long,
    val status: RequestStatus,
)
```

### 2. Business Rules Enforced in Domain

✅ **All business logic is in the domain model, NOT in services**

#### approveByTargetUser()

- Only PENDING requests can be approved
- Throws `IllegalStateException` for invalid state transitions
- Automatically transfers shift ownership to target user
- Returns new immutable instance

#### rejectByTargetUser()

- Only PENDING requests can be rejected
- Throws `IllegalStateException` for invalid state transitions
- Keeps shift ownership with requester
- Returns new immutable instance

### 3. Design Principles Applied

✅ **Immutability** - All state changes return new instances (copy)
✅ **Encapsulation** - Business rules are inside the domain model
✅ **Fail-fast** - Invalid transitions throw exceptions immediately
✅ **Testability** - Comprehensive TDD test coverage
✅ **Single Responsibility** - Domain model owns its business logic
✅ **Idiomatic Kotlin** - Uses data classes, named parameters, check()

### 4. Shift Model Enhancement

Added `userId` property to support ownership transfer:

```kotlin
data class Shift(
    val id: Long,
    val status: ShiftStatus,
    val userId: Long,
)
```

## Test Coverage

All business rules are verified through comprehensive tests:

1. ✅ New requests start as PENDING
2. ✅ Target user can approve PENDING requests
3. ✅ Target user can reject PENDING requests
4. ✅ Cannot approve already APPROVED requests
5. ✅ Cannot approve already REJECTED requests
6. ✅ Cannot reject already APPROVED requests
7. ✅ Cannot reject already REJECTED requests
8. ✅ Approval transfers shift ownership to target user
9. ✅ Rejection keeps shift ownership with requester
10. ✅ Immutability is maintained (original objects unchanged)

## Usage Example

```kotlin
// Create a shift request
val shift = Shift(id = 1L, status = ShiftStatus.APPROVED, userId = 100L)
val request = ShiftRequest(
    id = 1L,
    shift = shift,
    requesterId = 100L,
    targetUserId = 200L,
    status = RequestStatus.PENDING
)

// Target user approves the request
val approvedRequest = request.approveByTargetUser()
// Result: status = APPROVED, shift.userId = 200L

// Target user rejects the request
val rejectedRequest = request.rejectByTargetUser()
// Result: status = REJECTED, shift.userId = 100L (unchanged)

// Invalid transition (throws exception)
approvedRequest.approveByTargetUser()
// Throws: IllegalStateException("Only PENDING requests can be approved (was APPROVED)")
```

## Files Created/Modified

### Created:

- `src/main/kotlin/com/example/shiftapp/domain/RequestStatus.kt`
- `src/main/kotlin/com/example/shiftapp/domain/ShiftRequest.kt`
- `src/test/kotlin/com/example/shiftapp/domain/ShiftRequestTest.kt`

### Modified:

- `src/main/kotlin/com/example/shiftapp/domain/Shift.kt` (added userId)
- `src/test/kotlin/com/example/shiftapp/service/ShiftServiceTest.kt` (updated for userId)

## Key Benefits

1. **Type Safety** - Kotlin's type system prevents invalid states
2. **Clear Intent** - Method names explicitly describe business operations
3. **No Service Pollution** - Services remain thin orchestrators
4. **Easy to Test** - Pure domain logic with no dependencies
5. **Maintainable** - Business rules are centralized and discoverable
6. **Extensible** - Easy to add new business rules or status transitions

## Running Tests

```bash
./gradlew test --tests "com.example.shiftapp.domain.ShiftRequestTest"
```

All tests pass ✅
