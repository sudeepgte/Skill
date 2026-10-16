# Course Submission Lifecycle

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
