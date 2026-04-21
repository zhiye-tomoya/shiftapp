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

Great questions! Let me answer both:

## Question 1: About `shift.userId` at different approval stages

I think you meant `shift.userId` (not `shift.id`). Based on the code, here's what happens:

**At `RequestStatus.TARGET_APPROVED`:**

- ✅ The `shift.userId` has **already been transferred** to the target user (the person accepting the shift)
- This happens in the `approveByTargetUser()` method (line 58 of ShiftRequest.kt): `val transferredShift = shift.copy(userId = targetUserId)`
- Your test correctly asserts this: `assertEquals(200L, result.shift.userId)` at line 51

**At `RequestStatus.ADMIN_APPROVED`:**

- The `shift.userId` **remains** with the target user (no further transfer)
- The admin approval just finalizes the request status, ownership doesn't change
- Your test correctly asserts this: `assertEquals(200L, result.shift.userId)` at line 91

**However, there's a potential issue in your test setup!** At line 77-83 in `should_retrieve_approve_and_save_request_when_approved_by_admin()`, you're creating a shift with `userId = 100L` but status `TARGET_APPROVED`. This is inconsistent because once a request reaches `TARGET_APPROVED`, the shift ownership should already be `200L` (the target user).

The test should probably initialize the shift with `userId = 200L` on line 77 to reflect that ownership was already transferred.

## Question 2: Should `REJECTED` be split into `ADMIN_REJECTED` and `TARGET_REJECTED`?

This is a great design consideration! Here are the tradeoffs:

**Pros of splitting:**

- ✅ Better auditing - immediately know who rejected just from the status
- ✅ Could enable different business logic (e.g., "admin rejections require notification email")
- ✅ More explicit state machine
- ✅ Could help with different rollback logic (if you ever need to revert shift ownership based on who rejected)

**Cons of splitting:**

- ❌ More enum values to maintain
- ❌ May complicate queries if you just want "all rejected requests regardless of who rejected"
- ❌ Could track rejection source through other means (e.g., `rejectedBy` field)

**My recommendation:** **Yes, split them.** Here's why:

1. The two rejection paths have different contexts: `TARGET_REJECTED` means the target user declined to take the shift (ownership never transferred), while `ADMIN_REJECTED` means the target user agreed but admin vetoed (ownership may need to be rolled back)
2. This distinction could be important for future features like notifications, analytics, or rollback logic
3. The state machine becomes more explicit and easier to understand

Would you like me to propose a refactoring plan to:

1. Fix the test setup inconsistency?
2. Split REJECTED into ADMIN_REJECTED and TARGET_REJECTED?

Perfect choice! Option 2 is cleaner and simpler. Here's the detailed refactoring plan:

## Refactoring Plan for Option 2

### Changes Summary

**Key principle:** Shift ownership **only transfers on final admin approval**, not on target approval.

### 1. **RequestStatus.kt**

```kotlin
enum class RequestStatus {
    PENDING,
    TARGET_APPROVED,
    ADMIN_APPROVED,
    TARGET_REJECTED,  // NEW - Target user declined
    ADMIN_REJECTED,   // NEW - Admin vetoed after target approved
}
```

### 2. **ShiftRequest.kt - Domain Logic Changes**

**`approveByTargetUser()`** (line 52-64)

- **REMOVE** ownership transfer: ~~`val transferredShift = shift.copy(userId = targetUserId)`~~
- Keep shift ownership unchanged (stays with requester)
- Return `TARGET_APPROVED` status

**`approveByAdmin()`** (line 96-102)

- **ADD** ownership transfer: `val transferredShift = shift.copy(userId = targetUserId)`
- Transfer shift to target user (this is when actual ownership changes!)
- Return `ADMIN_APPROVED` status

**`rejectByTargetUser()`** (line 76-82)

- Change status from `REJECTED` → `TARGET_REJECTED`
- No ownership change (already with requester)

**`rejectByAdmin()`** (line 115-121)

- Change status from `REJECTED` → `ADMIN_REJECTED`
- No ownership change needed (still with requester since target approval didn't transfer it)

### 3. **ShiftRequestService.kt**

- Add `rejectByAdmin()` method (currently missing)

### 4. **ShiftRequestServiceTest.kt**

**Fix `should_retrieve_approve_and_save_request_when_approved_by_target_user()`** (line 36-54)

- Line 51: Change assertion from `assertEquals(200L, result.shift.userId)`
- To: `assertEquals(100L, result.shift.userId)` ← ownership stays with requester!

**Fix `should_retrieve_approve_and_save_request_when_approved_by_admin()`** (line 76-94)

- Line 77: Keep shift initialization with `userId = 100L` ← correct now!
- Line 91: Change assertion to verify ownership DOES transfer here: `assertEquals(200L, result.shift.userId)`

**Update `should_retrieve_reject_and_save_request_when_rejected_by_target_user()`** (line 56-74)

- Line 70: Change from `REJECTED` → `TARGET_REJECTED`

**Implement `should_retrieve_reject_and_save_request_when_rejected_by_admin()`** (line 96-98)

- Create test with `TARGET_APPROVED` initial status
- Verify status changes to `ADMIN_REJECTED`
- Verify ownership stays with requester (100L)

### 5. **ShiftRequestTest.kt (domain unit tests)**

- Add tests for the domain logic changes

---

**Ready to implement?** Please **toggle to Act mode** and I'll make all these changes!
