# Developer 2 Documentation

## Responsibilities
As Developer 2 on the SkillHub project, my primary ownership encompasses the **Instructor and Course Lifecycle**:
- Instructor Foundation, Dashboard, and Profile
- Course Management, Creation, and Editing
- Curriculum, Sections, Lectures, and Course Media
- Course Pricing and Course Status (Submission Lifecycle)
- Instructor Analytics

## Shared Responsibilities & Boundaries
- **Developer 1**: Owns Authentication, Security, User foundation, Payment backend, and common backend infrastructure. I rely on D1 for user identity (Principal) and payment processing.
- **Developer 3**: Owns Student enrollment, Learning progress, Exams, Certificates, Admin reviews, etc. I rely on D3 for course approval and provide them with course structures for student consumption.

## Important Dependencies
- `Course` relies on `User` for instructor mapping (D1 integration).
- `Course` relies on `Category` (Shared).
- Instructor Analytics relies on `Enrollment` (D3 integration) and `Review` (D3 integration).

## Course Lifecycle Overview
1. **Draft**: Instructor creates a course (saved as Draft).
2. **Pending Review**: Instructor submits the course for admin review.
3. **Published**: Admin (D3) approves the course, making it visible to students.
4. **Rejected/Archived**: Handled by admin review logic.

## Documentation Index
- [Instructor Audit](INSTRUCTOR_AUDIT.md)
- [Course Management Audit](COURSE_MANAGEMENT_AUDIT.md)
- [Curriculum & Sections](CURRICULUM_SECTIONS_LECTURES.md)
- [Course Media](COURSE_MEDIA.md)
- [Course Pricing](COURSE_PRICING.md)
- [Course Submission Lifecycle](COURSE_SUBMISSION_LIFECYCLE.md)
- [Instructor Analytics](INSTRUCTOR_ANALYTICS.md)
- [API Map](API_MAP.md)
- [Database Map](DATABASE_MAP.md)
- [Dependency Map](DEPENDENCY_MAP.md)
- [Security Audit](SECURITY_AUDIT.md)
- [Code Quality Audit](CODE_QUALITY_AUDIT.md)
- [Testing Audit](TESTING_AUDIT.md)
- [Implementation Plan](IMPLEMENTATION_PLAN.md)
