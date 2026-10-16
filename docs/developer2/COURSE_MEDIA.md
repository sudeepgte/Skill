# Course Media Audit

## Current Architecture
- **File Upload**: Handled by FileStorageService.
- **Thumbnails & Promos**: Stored as imageUrl and ideoUrl strings in Course entity.
- **Lecture Videos**: Stored as ideoUrl in Lecture.

## Access Control & Limits
- Configured in pplication.properties: 100MB limits.
- Files are saved to local uploads/ directory.

## Problems Identified
- Unrestricted file upload vulnerabilities (lack of content-type checking in InstructorController besides frontend accept attributes).
- Direct access to uploads/ might expose draft videos.

## Recommended Improvements
- Implement strict server-side MIME type verification.
- Use pre-signed URLs or streaming controllers for authorized access only.
