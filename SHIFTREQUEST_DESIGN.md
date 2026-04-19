# ShiftRequest Domain Model - Design Summary

## Overview

A rich domain model implementation of `ShiftRequest` following Domain-Driven Design principles with Test-Driven Development.

## Architecture

### 1. Domain Models Created

#### RequestStatus Enum

```kotlin
enum class RequestStatus {
    PENDING,
    TARGET_APPROVED,  // Target user has approved, awaiting admin approval
    ADMIN_APPROVED,   // Final approval by admin (swap is complete)
    REJECTED,
}
```

**2-Step Approval Flow:**

1. Target user approves: PENDING → TARGET_APPROVED
2. Admin approves: TARGET_APPROVED → ADMIN_APPROVED (final)

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

- Only PENDING requests can be approved by target user
- Throws `IllegalStateException` for invalid state transitions
- Automatically transfers shift ownership to target user
- Returns new immutable instance with TARGET_APPROVED status

#### rejectByTargetUser()

- Only PENDING requests can be rejected by target user
- Throws `IllegalStateException` for invalid state transitions
- Keeps shift ownership with requester
- Returns new immutable instance

#### approveByAdmin()

- Only TARGET_APPROVED requests can be approved by admin (second step)
- Throws `IllegalStateException` for invalid state transitions
- Shift ownership remains with target user (already transferred)
- Returns new immutable instance with ADMIN_APPROVED status (final)

#### rejectByAdmin()

- Only TARGET_APPROVED requests can be rejected by admin
- Admin can reject even after target user approved
- Throws `IllegalStateException` for invalid state transitions
- Returns new immutable instance with REJECTED status

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

All business rules are verified through comprehensive tests (13 tests total):

**Target User Approval/Rejection:**

1. ✅ New requests start as PENDING
2. ✅ Target user can approve PENDING requests (→ TARGET_APPROVED)
3. ✅ Target user can reject PENDING requests
4. ✅ Cannot approve already TARGET_APPROVED requests
5. ✅ Cannot approve already REJECTED requests
6. ✅ Cannot reject already TARGET_APPROVED requests
7. ✅ Cannot reject already REJECTED requests
8. ✅ Approval transfers shift ownership to target user
9. ✅ Rejection keeps shift ownership with requester
10. ✅ Immutability is maintained (original objects unchanged)

**Admin Approval/Rejection (2-Step):** 11. ✅ Admin can approve TARGET_APPROVED requests (→ ADMIN_APPROVED) 12. ✅ Admin cannot approve PENDING requests (must be TARGET_APPROVED first) 13. ✅ Admin can reject TARGET_APPROVED requests 14. ✅ Admin cannot reject PENDING requests (must be TARGET_APPROVED first)

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

// ===== 2-Step Approval Flow (Happy Path) =====

// Step 1: Target user approves the request
val targetApproved = request.approveByTargetUser()
// Result: status = TARGET_APPROVED, shift.userId = 200L

// Step 2: Admin approves the request (final step)
val adminApproved = targetApproved.approveByAdmin()
// Result: status = ADMIN_APPROVED, shift.userId = 200L
// ✅ Shift swap is now complete!

// ===== Alternative: Target user rejects =====

val rejectedByTarget = request.rejectByTargetUser()
// Result: status = REJECTED, shift.userId = 100L (unchanged)

// ===== Alternative: Admin rejects after target approval =====

val targetApproved2 = request.approveByTargetUser()
val rejectedByAdmin = targetApproved2.rejectByAdmin()
// Result: status = REJECTED (admin can reject even after target approved)

// ===== Invalid transitions (throw exceptions) =====

// Cannot approve TARGET_APPROVED request as target user
targetApproved.approveByTargetUser()
// Throws: IllegalStateException("Only PENDING requests can be approved (was TARGET_APPROVED)")

// Cannot admin-approve a PENDING request (target must approve first)
request.approveByAdmin()
// Throws: IllegalStateException("Only TARGET_APPROVED requests can be admin-approved (was PENDING)")
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
