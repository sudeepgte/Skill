import os

docs_dir = r'c:\Users\priya\Desktop\skill\Skill\docs\developer2'
if not os.path.exists(docs_dir):
    os.makedirs(docs_dir)

files = {
    'INSTRUCTOR_AUDIT.md': '''# Instructor Foundation Audit

## Current Architecture
- **Instructor Entity**: User model handles both students and instructors, differentiated by roles (handled by D1).
- **Profile**: Fields like bio, skills, expertise, and social links are embedded in User.java.
- **APIs**: InstructorController handles dashboard routes and profile updates.

## Dependencies
- **Authentication**: D1 handles login and JWT tokens.
- **Roles**: D1 handles role assignments.

## Problems Identified
- updateProfile method expects MultipartFile profilePicture but relies on ileStorageService. Needs proper validation of file types.
- Password change logic is mixed in the controller instead of a dedicated service.

## Recommended Improvements
- Delegate password validation and updates to D1's UserService.
- Implement robust image validation before uploading to ileStorageService.
''',

    'COURSE_MANAGEMENT_AUDIT.md': '''# Course Management Audit

## Current Architecture
- **Entity**: Course (15+ fields including price, 	itle, imageUrl, etc.).
- **Controller**: InstructorController handles course CRUD for vendors.
- **Service**: CourseService handles business logic.

## Course Creation & Editing
- Basic information, curriculum structure, pricing, and media are part of the course forms.
- Missing subCategory and longDescription fields from frontend forms.

## Dependencies
- D3 depends on Course to link enrollments and exams.

## Problems Identified
- Course creation defaults to public immediately.
- Form doesn't persist Curriculum order (missing positions).

## Recommended Improvements
- Implement Course Status enum and Draft -> Pending -> Published lifecycle (partially addressed).
- Add hidden position fields for curriculum items (addressed).
''',

    'CURRICULUM_SECTIONS_LECTURES.md': '''# Curriculum, Sections, and Lectures Audit

## Current Architecture
- **Curriculum Model**: Implemented as a nested hierarchy (Course -> CourseSection -> Lecture).
- **Entity Relationships**:
  - Course has @OneToMany with CourseSection.
  - CourseSection has @OneToMany with Lecture.

## Operations & Ordering
- Instructor can add sections and lectures on the same page.
- Frontend uses JavaScript to dynamically generate input fields.

## Validation & Transactions
- Standard @ModelAttribute binding is used. No strict transaction boundaries identified for deep nested updates.

## Problems Identified
- Order not preserved correctly out of the box due to missing position array binding in HTML forms.
- Deletion of a section might leave orphaned lectures if cascade is misconfigured.

## Recommended Improvements
- Add @Transactional to section/lecture deletion APIs.
- Ensure strict ordering using index tracking on the frontend (fixed).
''',

    'COURSE_MEDIA.md': '''# Course Media Audit

## Current Architecture
- **File Upload**: Handled by FileStorageService.
- **Thumbnails & Promos**: Stored as imageUrl and ideoUrl strings in Course entity.
- **Lecture Videos**: Stored as ideoUrl in Lecture.

## Access Control & Limits
- Configured in pplication.properties: 100MB limits.
- Files are saved to local uploads/ directory.

## Problems Identified
- Unrestricted file upload vulnerabilities (lack of content-type checking in InstructorController besides frontend accept attributes).
- Direct access to uploads/ might expose draft videos.

## Recommended Improvements
- Implement strict server-side MIME type verification.
- Use pre-signed URLs or streaming controllers for authorized access only.
''',

    'COURSE_PRICING.md': '''# Course Pricing Audit

## Current Architecture
- **Model**: Embedded directly in Course (price, salePrice).
- **Currency**: Implicitly INR (hardcoded in frontend).
- **Free vs Paid**: Checkbox in frontend toggles isFree which sets price = 0.

## Integration
- **Payment Backend**: Developer 1 utilizes price for Razorpay payment initialization.
- **Checkout**: Developer 3 reads price for student display.

## Problems Identified
- No tracking of price changes.
- Price can be negative or exorbitant if frontend validation is bypassed (backend lacks @Min constraints on price).

## Recommended Improvements
- Add @Min(0) to Course pricing fields.
- Document strict contract for D1 payment gateways.
''',

    'COURSE_SUBMISSION_LIFECYCLE.md': '''# Course Submission Lifecycle

## Current Architecture
- **Status Values**: Historically relied on oolean isPublished. Transitioning to String status (DRAFT, PENDING_REVIEW, PUBLISHED).
- **Transitions**: Instructor creates as Draft, submits for Review. Admin publishes.

## Draft & Review States
- Drafts are only visible to the instructor.
- PENDING_REVIEW signals Developer 3's Admin dashboard to review the course.

## Required Fields Validation
- Minimal backend validation before submission; mostly relies on frontend HTML5 required attributes.

## Problems Identified
- Missing strict backend validation before allowing a state change to PENDING_REVIEW.

## Recommended Improvements
- Add a validation service that checks if curriculum has at least 1 section and 1 lecture before allowing submission.
''',

    'INSTRUCTOR_ANALYTICS.md': '''# Instructor Analytics Audit

## Current Architecture
- **Dashboard**: InstructorController.dashboard gathers all stats.
- **Metrics**: Total students, Total views, Average rating, Total exams passed.

## Data Sources
- Iterates over course.getEnrollments() (D3).
- Iterates over courseQuestionRepository (D3).

## Problems Identified
- **N+1 Query Issue**: Iterating through all courses and calling .size() on lazy loaded collections.
- Performance will degrade severely for instructors with large enrollments.

## Recommended Improvements
- Replace iterative Java loops with JPQL aggregate queries (e.g. SELECT count(e) FROM Enrollment e WHERE e.course.instructor = :inst).
''',

    'API_MAP.md': '''# API Map

## Existing APIs
| Method | Endpoint | Controller | Service | Auth | Consumer |
|---|---|---|---|---|---|
| GET | /vendor/dashboard | InstructorController | CourseService | Auth | Instructor UI |
| POST | /vendor/courses/save | InstructorController | CourseService | Auth | Instructor UI |
| POST | /vendor/courses/{id}/delete | InstructorController | CourseService | Auth | Instructor UI |
| POST | /vendor/courses/{id}/submit | InstructorController | CourseService | Auth | Instructor UI |
| POST | /vendor/profile/update | InstructorController | UserService | Auth | Instructor UI |

## Proposed Cross-Developer Contracts
- GET /api/internal/courses/{id}/pricing (D2 -> D1)
- POST /api/internal/courses/{id}/publish (D3 -> D2)
''',

    'DATABASE_MAP.md': '''# Database Ownership & Map

## Developer 2 Owned Entities
- Course
- CourseSection
- Lecture

## Shared / Cross-Module
- Course -> Category (Shared lookup)
- Course -> User (Owned by D1)
- Enrollment -> Course (Owned by D3)
- Review -> Course (Owned by D3)

## Constraints & Indexes
- Foreign key from CourseSection to Course.
- Foreign key from Lecture to CourseSection.
- Missing index on instructor_id in Course table, causing slow lookups for dashboards.
''',

    'DEPENDENCY_MAP.md': '''# Dependency Map

## D2 → D1
- InstructorController depends on UserService.findByEmail (Identity).
- InstructorController depends on PasswordEncoder (Profile updates).

## D2 ↔ D3
- Course depends on Enrollment collection (D3 entity) for analytics.
- Course depends on ExamQuestion and CourseQuestion.
- Admin (D3) depends on CourseService.publishCourse().

## Diagram
`mermaid
flowchart TD
    D2[Course Management] -->|Verifies Auth| D1[User/Security]
    D3[Admin/Student] -->|Reads/Updates| D2
    D2 -->|Reads Enrollments| D3
`
''',

    'SECURITY_AUDIT.md': '''# Security Audit

## Vulnerabilities

### Unrestricted File Upload
- **Severity**: High
- **Location**: InstructorController.saveCourse, ileStorageService
- **Problem**: No backend MIME type or magic byte validation for thumbnails and videos.
- **Fix**: Validate file extension and MIME type before saving.

### IDOR Risk in Section/Lecture Deletion
- **Severity**: Medium
- **Location**: InstructorController
- **Problem**: Some sub-resource endpoints (like deleting a lecture) may lack verification that the current user actually owns the parent course.
- **Fix**: Always verify course.getInstructor().getEmail().equals(principal.getName()) deep down to the lecture level.
''',

    'CODE_QUALITY_AUDIT.md': '''# Code Quality Audit

## Observations
- **Large Controllers**: InstructorController handles course CRUD, profile updates, analytics, and messaging. It should be split into InstructorCourseController, InstructorProfileController, and InstructorAnalyticsController.
- **Tight Coupling**: Analytics logic is hardcoded inside the controller methods rather than abstracted into a DashboardAnalyticsService.
- **N+1 Queries**: Extensive use of eager/lazy iterations inside loops (e.g. course.getEnrollments().size()) triggers massive N+1 select problems.

## Recommended Improvements
- Implement DTOs for dashboard statistics instead of binding the whole User and Course entities directly to the view.
- Refactor analytics to JPQL.
''',

    'TESTING_AUDIT.md': '''# Testing Audit

## Existing Tests
- Basic Spring Boot context loads tests.
- Little to no dedicated unit/integration tests for Developer 2 modules.

## Missing Test Coverage
- **Instructor Ownership Enforcement**: Assert that User A cannot edit User B's course.
- **Course Creation Validation**: Assert title format, price constraints.
- **Curriculum Ordering**: Verify array indexing is persisted correctly.
- **Status Transitions**: Assert Draft -> Pending -> Published state machine rules.

## Recommended Testing Strategy
- Introduce @DataJpaTest for CourseRepository validations.
- Introduce @WebMvcTest for InstructorController ownership verification.
'''
}

for name, content in files.items():
    filepath = os.path.join(docs_dir, name)
    with open(filepath, 'w', encoding='utf-8') as f:
        f.write(content)

print("Files created successfully.")
