# Testing Audit

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
