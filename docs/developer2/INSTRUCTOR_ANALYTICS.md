# Instructor Analytics Audit

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
