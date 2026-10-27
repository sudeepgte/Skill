# SkillHub — QA Defect Fixing & Resolution Report

**Date:** 2026-10-27  
**Auditor / Engineer:** Principal Spring Boot Developer & QA Automation Engineer  
**Target Environment:** Spring Boot 3.5.5 / Java 17 / MySQL 8 / Thymeleaf / Spring Security 6  
**Application Base URL:** `http://localhost:8085`  
**Test Suite Status:** 214 Passed / 0 Failures / 0 Errors / 0 Skipped (35 Test Suites)  
**Targeted QA Integration Tests:** 14 Passed / 0 Failures / 0 Errors (`QaDefectFixesIntegrationTest`)

---

## 1. Executive Summary & Defect Reconciliation

An external QA report was submitted detailing verification of the SkillHub platform. The report presented the following metrics:
- **Total Executed Tests:** 78
- **Passed:** 74
- **Reported Failures:** 3
- **Reported UI/UX Warnings:** 1 (combined under viewport/responsive design)

### Discrepancy Reconciliation (Task 4)
| Item | Category | Description | Prior Status | Resolution & Current Status |
|---|---|---|---|---|
| **DEF-01** | UI / Responsive | Missing `<meta name="viewport">` and mobile horizontal overflow in placement & admin reports | FAILED / WARNING | **RESOLVED & VERIFIED**: Viewport tags and responsive wrappers added across all 4 templates. |
| **DEF-02** | Backend Routing | `GET /vendor/courses` returned HTTP 404 Not Found | FAILED | **RESOLVED & VERIFIED**: Added `@GetMapping("/courses")` in `InstructorController` redirecting to `/vendor/dashboard?tab=courses` preserving query params. |
| **DEF-03** | API / Integration | Wishlist endpoints rejected mismatched content-types and duplicate rapid submissions | FAILED | **RESOLVED & VERIFIED**: Polymorphic content-type negotiation (`application/json` and `!application/json`) implemented on both `/course/wishlist/add` and `/api/wishlist/add`. Rapid double-click handled gracefully. |
| **Baseline** | Full Regression | Regression suite across all application modules | 200 passing | **VERIFIED**: Complete test suite executed at 214 tests passing, 0 failures, 0 errors, 0 skipped. |

---

## 2. Defect 1: Missing Viewport Meta Tags & Mobile Responsiveness

### Root Cause
Four legacy templates were authored without standard mobile viewport metadata `<meta name="viewport" content="width=device-width, initial-scale=1.0">`. Consequently, mobile browsers rendered the desktop canvas scaled down, resulting in microscopic typography, broken layouts, and non-responsive tables overflowing viewports on mobile devices.

### Modified Files
1. `src/main/resources/templates/placement/dashboard.html`
2. `src/main/resources/templates/placement/aptitude_result.html`
3. `src/main/resources/templates/placement/interview_questions.html`
4. `src/main/resources/templates/admin/reports.html`

### Changes Implemented
- Inserted `<meta name="viewport" content="width=device-width, initial-scale=1.0">` into `<head>` across all four files.
- Added responsive CSS media queries (`@media (max-width: 768px)`):
  - In `dashboard.html`: stats cards grid converted to responsive 1-column layout, action buttons set to full width.
  - In `aptitude_result.html`: score circles scaled down to 100px for mobile screens, breakdown grid stacked vertically.
  - In `interview_questions.html`: category filters made horizontally scrollable with `-webkit-overflow-scrolling: touch;`, question cards padded appropriately.
  - In `reports.html`: wrapped financial and analytical tables in `.reports-table-wrapper` with `overflow-x: auto` and `-webkit-overflow-scrolling: touch` to prevent desktop layout clipping.
- Verified that each template contains exactly one viewport tag.

---

## 3. Defect 2: Missing Instructor Courses Route (`GET /vendor/courses`)

### Root Cause
The instructor navigation UI and external bookmarks referenced `/vendor/courses`. However, `InstructorController.java` only exposed `/vendor/dashboard`, leaving `/vendor/courses` unmapped, which returned an unhandled HTTP 404 Not Found.

### Modified Files
1. `src/main/java/com/skillhub/controller/InstructorController.java`

### Changes Implemented
- Added `@GetMapping("/courses")` to `InstructorController`:
  ```java
  @GetMapping("/courses")
  @PreAuthorize("hasAnyAuthority('INSTRUCTOR', 'ADMIN', 'ROLE_INSTRUCTOR', 'ROLE_ADMIN')")
  public String getVendorCourses(
          @RequestParam(value = "status", required = false) String status,
          @RequestParam(value = "page", required = false) Integer page,
          @RequestParam(value = "size", required = false) Integer size,
          org.springframework.web.servlet.mvc.support.RedirectAttributes redirectAttributes) {
      
      StringBuilder redirectUrl = new StringBuilder("redirect:/vendor/dashboard?tab=courses");
      if (status != null && !status.trim().isEmpty()) {
          redirectUrl.append("&status=").append(status.trim());
      }
      if (page != null) {
          redirectUrl.append("&page=").append(page);
      }
      if (size != null) {
          redirectUrl.append("&size=").append(size);
      }
      return redirectUrl.toString();
  }
  ```
- **Redirection Behavior:** Redirects with HTTP 302 to `/vendor/dashboard?tab=courses`.
- **Parameter Preservation:** Preserves `status`, `page`, and `size` parameters during the redirect.
- **Security & Authorization:** Enforces `@PreAuthorize("hasAnyAuthority('INSTRUCTOR', 'ADMIN', 'ROLE_INSTRUCTOR', 'ROLE_ADMIN')")`. Unauthenticated requests redirect to `/login` via Spring Security's entry point; unauthorized roles (e.g. `STUDENT`) receive HTTP 403 Forbidden.

---

## 4. Defect 3: Wishlist API Parameter & Content-Type Inconsistency

### Root Cause
Two separate endpoints handled wishlist operations:
- `/course/wishlist/add` in `CourseController.java` (expected form-encoded request)
- `/api/wishlist/add` in `WishlistRestController.java` (expected JSON body)

When frontend or third-party clients posted `application/x-www-form-urlencoded` to `/api/wishlist/add` or `application/json` to `/course/wishlist/add`, Spring threw `HttpMediaTypeNotSupportedException` or unhandled deserialization errors. Furthermore, duplicate additions caused unhandled unique constraint violations (`DataIntegrityViolationException`) returning HTTP 500 instead of an idempotent HTTP 200 response, and rapid clicking on the course details page lacked UI debouncing.

### Modified Files
1. `src/main/java/com/skillhub/controller/CourseController.java`
2. `src/main/java/com/skillhub/controller/WishlistRestController.java`
3. `src/main/resources/templates/course-details.html`

### Changes Implemented
- **Polymorphic Content-Type Negotiation:**
  - Implemented dual mapped endpoints partitioned by `consumes = MediaType.APPLICATION_JSON_VALUE` and `consumes = "!application/json"`:
    - JSON requests parse `courseId` from request body (supporting numbers and numeric strings).
    - Form/Query parameter requests parse `courseId` from `@RequestParam(value = "courseId", required = false)`.
    - Both delegates route through a shared idempotent handler method.
- **Graceful Duplicate Handling:**
  - Checked `wishlistRepository.findByUserAndCourse(user, course).isPresent()` before saving.
  - Wrapped `wishlistRepository.save(wishlist)` in a `try-catch` block catching `DataIntegrityViolationException` to handle race conditions gracefully.
  - Both conditions return `{ "success": true, "alreadyAdded": true, "alreadyInWishlist": true, "message": "Course already in wishlist" }` with HTTP 200 OK.
- **Frontend Debouncing & UI Guard:**
  - In `src/main/resources/templates/course-details.html`, added `data-submitting="true"` attribute guard and button disabling while wishlist requests are in-flight.
  - Updated response handler to recognize `alreadyAdded: true` and inform the user without throwing errors.
  - Maintained full CSRF token propagation (`X-CSRF-TOKEN` / `_csrf`).

---

## 5. Verification & Test Evidence

### Targeted QA Integration Test Suite (`QaDefectFixesIntegrationTest`)
A comprehensive integration test suite was authored in `src/test/java/com/skillhub/controller/QaDefectFixesIntegrationTest.java` containing 14 automated tests:

| Test Method | Defect Target | Assertion & Outcome | Result |
|---|---|---|---|
| `testPlacementDashboardContainsViewportTag` | DEF-01 | Viewport meta tag present in rendered HTML | **PASS** |
| `testPlacementInterviewQuestionsContainsViewportTag` | DEF-01 | Viewport meta tag present in rendered HTML | **PASS** |
| `testAdminReportsContainsViewportTag` | DEF-01 | Viewport meta tag and `reports-table-wrapper` present | **PASS** |
| `testInstructorCoursesRouteRedirectsToDashboardTab` | DEF-02 | GET `/vendor/courses` -> 302 redirect to `/vendor/dashboard?tab=courses` | **PASS** |
| `testInstructorCoursesRoutePreservesQueryParams` | DEF-02 | Redirect preserves `status=PUBLISHED&page=2&size=25` | **PASS** |
| `testUnauthenticatedAccessToVendorCoursesRedirectsToLogin` | DEF-02 | Anonymous request with Accept text/html -> 302 to `/login` | **PASS** |
| `testStudentAccessToVendorCoursesIsForbidden` | DEF-02 | Authenticated `ROLE_STUDENT` -> HTTP 403 Forbidden | **PASS** |
| `testCourseWishlistAddWithFormUrlEncoded` | DEF-03 | Form POST to `/course/wishlist/add` -> HTTP 200, persists wishlist | **PASS** |
| `testCourseWishlistAddWithJsonBody` | DEF-03 | JSON POST to `/course/wishlist/add` -> HTTP 200, persists wishlist | **PASS** |
| `testApiWishlistAddWithJsonBody` | DEF-03 | JSON POST to `/api/wishlist/add` -> HTTP 200, persists wishlist | **PASS** |
| `testApiWishlistAddWithFormParam` | DEF-03 | Form POST to `/api/wishlist/add` -> HTTP 200, persists wishlist | **PASS** |
| `testDuplicateWishlistAdditionHandledGracefully` | DEF-03 | Rapid double-add & cross-endpoint add return `alreadyAdded: true` (200 OK) | **PASS** |
| `testUnauthenticatedWishlistAddHandledProperly` | DEF-03 | Unauth `/course/wishlist/add` -> redirect `/login`; unauth `/api/**` -> 401 | **PASS** |
| `testWishlistRemovalEndpoints` | DEF-03 | Removal via `/api/wishlist/remove` and `/course/wishlist/remove` verified | **PASS** |

### Complete Regression Verification
- **Command:** `.\mvnw.cmd test`
- **Total Test Suites Executed:** 35
- **Total Tests Executed:** 214
- **Failures:** 0
- **Errors:** 0
- **Skipped:** 0
- **Total Execution Time:** 02:58 min

### Artifact Packaging
- **Command:** `.\mvnw.cmd package -DskipTests`
- **Output Artifact:** `target/skillhub-0.0.1-SNAPSHOT.war`
- **Status:** `BUILD SUCCESS` (Spring Boot executable repackaged archive generated).
