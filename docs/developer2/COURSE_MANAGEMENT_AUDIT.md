# Course Management Audit

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
