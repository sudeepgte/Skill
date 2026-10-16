# Dependency Map

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
"@

    "SECURITY_AUDIT.md" = @"
# Security Audit

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
