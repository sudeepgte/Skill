# API Map

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
