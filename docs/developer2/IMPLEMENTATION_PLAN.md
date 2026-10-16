# Developer 2 Implementation Plan

## D2-COURSE-001
**Priority**: P0
**Module**: Course Submission Lifecycle
**Problem**: Course creation immediately sets `isPublished = true` and skips the Draft -> Pending Review -> Published workflow.
**Current implementation**: `InstructorController.saveCourse` calls `course.setPublished(true);`.
**Files**: `src/main/java/com/skillhub/controller/InstructorController.java`, `src/main/java/com/skillhub/model/Course.java`
**Root cause**: Missing `status` Enum/field in `Course.java`, causing courses to bypass the submission lifecycle.
**Proposed change**: Add a `status` field to `Course.java` defaulting to `"DRAFT"`. Update `setPublished` to sync the state. Update `InstructorController` to save as `DRAFT` and add a `/submit` endpoint.
**Dependencies**: D3 handles admin approval which will call `setPublished(true)`. D1/D3 currently query by `isPublished = true`.
**Developer 2 responsibility**: Add state tracking, UI display for status, and submission action.
**Other developer dependency**: D3 must transition `PENDING_REVIEW` to `PUBLISHED` in AdminController.
**Testing required**: Unit tests for status transitions; API tests for submission endpoint.
**Risk**: If D1/D3 queries are affected, it could hide courses. Mitigated by keeping `isPublished` boolean in sync.
**Expected result**: Instructors can save drafts and submit them, and courses only become public when approved.

## D2-COURSE-002
**Priority**: P0
**Module**: Curriculum Management
**Problem**: Sections and Lectures do not persist ordering when created.
**Current implementation**: `create_course.html` dynamically adds elements without a `position` parameter. The backend models (`CourseSection`, `Lecture`) have a `position` field, but it remains 0.
**Files**: `src/main/resources/templates/instructor/create_course.html`
**Root cause**: Missing hidden `<input name="sections[x].position" value="x">` fields in the frontend JS logic.
**Proposed change**: Inject hidden inputs for `position` in `addSection()` and `addLecture()` functions.
**Dependencies**: None.
**Developer 2 responsibility**: Correctly bind curriculum order on creation and edit.
**Testing required**: Integration test for course creation with multiple sections/lectures to verify `position` is populated.
**Risk**: Low.
**Expected result**: Curriculum preserves the exact order created by the instructor.

## D2-COURSE-003
**Priority**: P1
**Module**: Course Management
**Problem**: Missing required course fields in creation form.
**Current implementation**: `Course.java` has `subCategory` and `longDescription`, but `create_course.html` lacks these inputs.
**Files**: `src/main/resources/templates/instructor/create_course.html`, `src/main/resources/templates/instructor/edit_course.html`
**Root cause**: Form was partially implemented and didn't include all fields defined in the model.
**Proposed change**: Add input fields for `subCategory` and a rich text editor (or textarea) for `longDescription`.
**Dependencies**: None.
**Developer 2 responsibility**: Frontend form update.
**Expected result**: Instructors can fill out all details.

## D2-ANALYTICS-001
**Priority**: P2
**Module**: Instructor Analytics
**Problem**: The `totalViews` and `totalEnrolled` statistics use basic iteration rather than efficient database aggregation.
**Current implementation**: `InstructorController.dashboard` iterates over `courses` and sums `course.getEnrollments().size()`.
**Files**: `src/main/java/com/skillhub/controller/InstructorController.java`
**Root cause**: Lack of dedicated JPQL aggregation queries in `EnrollmentRepository` and `CourseRepository`.
**Proposed change**: Implement `countByCourseInstructorEmail()` and sum queries to prevent N+1 and memory overhead.
**Dependencies**: D3 owns `Enrollment`. I must verify API contracts or ask D3 for aggregate methods.
**Developer 2 responsibility**: Create performant analytics APIs.
**Other developer dependency**: D3 must expose/approve enrollment aggregates.
**Expected result**: Dashboard loads efficiently for instructors with many courses.
