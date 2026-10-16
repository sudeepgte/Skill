# Curriculum, Sections, and Lectures Audit

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
