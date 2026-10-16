# Instructor Foundation Audit

## Current Architecture
- **Instructor Entity**: User model handles both students and instructors, differentiated by roles (handled by D1).
- **Profile**: Fields like bio, skills, expertise, and social links are embedded in User.java.
- **APIs**: InstructorController handles dashboard routes and profile updates.

## Dependencies
- **Authentication**: D1 handles login and JWT tokens.
- **Roles**: D1 handles role assignments.

## Problems Identified
- updateProfile method expects MultipartFile profilePicture but relies on ileStorageService. Needs proper validation of file types.
- Password change logic is mixed in the controller instead of a dedicated service.

## Recommended Improvements
- Delegate password validation and updates to D1's UserService.
- Implement robust image validation before uploading to ileStorageService.
