# Database Ownership & Map

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
