# Code Quality Audit

## Observations
- **Large Controllers**: InstructorController handles course CRUD, profile updates, analytics, and messaging. It should be split into InstructorCourseController, InstructorProfileController, and InstructorAnalyticsController.
- **Tight Coupling**: Analytics logic is hardcoded inside the controller methods rather than abstracted into a DashboardAnalyticsService.
- **N+1 Queries**: Extensive use of eager/lazy iterations inside loops (e.g. course.getEnrollments().size()) triggers massive N+1 select problems.

## Recommended Improvements
- Implement DTOs for dashboard statistics instead of binding the whole User and Course entities directly to the view.
- Refactor analytics to JPQL.
