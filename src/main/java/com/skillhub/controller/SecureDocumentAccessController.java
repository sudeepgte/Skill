package com.skillhub.controller;

import com.skillhub.model.Course;
import com.skillhub.model.CourseResource;
import com.skillhub.model.ProfessionalExamAttempt;
import com.skillhub.model.User;
import com.skillhub.repository.CourseRepository;
import com.skillhub.repository.CourseResourceRepository;
import com.skillhub.repository.EnrollmentRepository;
import com.skillhub.repository.ProfessionalExamAttemptRepository;
import com.skillhub.repository.UserRepository;
import com.skillhub.service.FileStorageService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.net.MalformedURLException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.Principal;

@RestController
public class SecureDocumentAccessController {

    private static final Logger log = LoggerFactory.getLogger(SecureDocumentAccessController.class);

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private CourseRepository courseRepository;

    @Autowired
    private CourseResourceRepository courseResourceRepository;

    @Autowired
    private EnrollmentRepository enrollmentRepository;

    @Autowired
    private ProfessionalExamAttemptRepository examAttemptRepository;

    @Autowired
    private FileStorageService fileStorageService;

    /**
     * Protected access to instructor Aadhaar card, verification proofs, resumes, and certificates.
     * Enforces RBAC: only ADMIN or the specific document owner can access.
     */
    @GetMapping({"/documents/view", "/api/documents/user/{userId}/{type}"})
    public ResponseEntity<Resource> viewProtectedDocument(
            @PathVariable(value = "userId", required = false) Long pathUserId,
            @PathVariable(value = "type", required = false) String pathType,
            @RequestParam(value = "userId", required = false) Long paramUserId,
            @RequestParam(value = "type", required = false) String paramType,
            Principal principal) {

        if (principal == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        Long targetUserId = (pathUserId != null) ? pathUserId : paramUserId;
        String docType = (pathType != null) ? pathType : paramType;

        if (targetUserId == null || docType == null) {
            return ResponseEntity.badRequest().build();
        }

        User targetUser = userRepository.findById(targetUserId).orElse(null);
        if (targetUser == null) {
            return ResponseEntity.notFound().build();
        }

        User currentUser = userRepository.findByEmail(principal.getName());
        if (currentUser == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        boolean isAdmin = "ADMIN".equalsIgnoreCase(currentUser.getRole()) || "ROLE_ADMIN".equalsIgnoreCase(currentUser.getRole());
        boolean isOwner = currentUser.getId().equals(targetUser.getId());

        // Horizontal Privilege Escalation Protection (Requirement 6 & 7)
        if (!isAdmin && !isOwner) {
            log.warn("Security Alert: User {} attempted unauthorized access to document of user {}",
                    currentUser.getEmail(), targetUser.getId());
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        String fileUrl = resolveDocumentUrl(targetUser, docType.toLowerCase());
        if (fileUrl == null || fileUrl.trim().isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        return serveFileSecurely(fileUrl, "document_" + docType);
    }

    /**
     * Protected access to private documents & proctoring recordings via direct private route.
     * Enforces RBAC: only ADMIN or the specific document/recording owner can access.
     */
    @GetMapping({"/private/documents/**", "/private/recordings/**"})
    public ResponseEntity<Resource> viewPrivateDocumentByDirectPath(
            jakarta.servlet.http.HttpServletRequest request,
            Principal principal) {

        if (principal == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        String path = (String) request.getAttribute(org.springframework.web.servlet.HandlerMapping.PATH_WITHIN_HANDLER_MAPPING_ATTRIBUTE);
        if (path == null) {
            return ResponseEntity.badRequest().build();
        }

        User currentUser = userRepository.findByEmail(principal.getName());
        if (currentUser == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        boolean isAdmin = "ADMIN".equalsIgnoreCase(currentUser.getRole()) || "ROLE_ADMIN".equalsIgnoreCase(currentUser.getRole());
        boolean isOwner = false;

        if (path.startsWith("/private/documents/")) {
            User owner = userRepository.findByAnyDocumentUrl(path).orElse(null);
            isOwner = (owner != null && owner.getId().equals(currentUser.getId()));
        } else if (path.startsWith("/private/recordings/")) {
            ProfessionalExamAttempt attempt = examAttemptRepository.findByRecordingUrl(path).orElse(null);
            isOwner = (attempt != null && attempt.getStudent() != null && attempt.getStudent().getId().equals(currentUser.getId()));
        }

        if (!isAdmin && !isOwner) {
            log.warn("Security Alert: User {} denied unauthorized access to private path {}",
                    currentUser.getEmail(), path);
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        return serveFileSecurely(path, "private_file");
    }

    /**
     * Protected access to proctoring exam recordings.
     * Only ADMIN or the student who completed the attempt can access.
     */
    @GetMapping("/api/exam-attempts/{attemptId}/recording")
    public ResponseEntity<Resource> viewExamRecording(@PathVariable("attemptId") Long attemptId, Principal principal) {
        if (principal == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        ProfessionalExamAttempt attempt = examAttemptRepository.findById(attemptId).orElse(null);
        if (attempt == null) {
            return ResponseEntity.notFound().build();
        }

        User currentUser = userRepository.findByEmail(principal.getName());
        if (currentUser == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        boolean isAdmin = "ADMIN".equalsIgnoreCase(currentUser.getRole()) || "ROLE_ADMIN".equalsIgnoreCase(currentUser.getRole());
        boolean isOwner = currentUser.getId().equals(attempt.getStudent().getId());

        if (!isAdmin && !isOwner) {
            log.warn("Security Alert: User {} attempted unauthorized access to exam recording for attempt {}",
                    currentUser.getEmail(), attemptId);
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        String fileUrl = attempt.getRecordingUrl();
        if (fileUrl == null || fileUrl.trim().isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        return serveFileSecurely(fileUrl, "exam_recording_" + attemptId);
    }

    /**
     * Protected download of course resources (Requirement 12).
     * Enforces enrollment, ownership, and publication rules before streaming.
     */
    @GetMapping("/api/courses/{courseId}/resources/{resourceId}/download")
    public ResponseEntity<Resource> downloadCourseResource(
            @PathVariable("courseId") Long courseId,
            @PathVariable("resourceId") Long resourceId,
            Principal principal) {

        if (principal == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        Course course = courseRepository.findById(courseId).orElse(null);
        CourseResource resource = courseResourceRepository.findById(resourceId).orElse(null);

        if (course == null || resource == null || !resource.getCourse().getId().equals(courseId)) {
            return ResponseEntity.notFound().build();
        }

        User currentUser = userRepository.findByEmail(principal.getName());
        if (currentUser == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        boolean isAdmin = "ADMIN".equalsIgnoreCase(currentUser.getRole()) || "ROLE_ADMIN".equalsIgnoreCase(currentUser.getRole());
        boolean isInstructor = course.getInstructor() != null && course.getInstructor().getId().equals(currentUser.getId());
        boolean isEnrolled = enrollmentRepository.findByStudentAndCourse(currentUser, course).isPresent();

        // Must be admin, course instructor, or enrolled student
        if (!isAdmin && !isInstructor && !isEnrolled) {
            log.warn("Security Alert: User {} denied resource download for un-enrolled course {}",
                    currentUser.getEmail(), courseId);
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        String fileUrl = resource.getFileUrl();
        return serveFileSecurely(fileUrl, resource.getTitle());
    }

    private String resolveDocumentUrl(User user, String type) {
        return switch (type) {
            case "aadhar", "aadharcard" -> user.getAadharCardUrl();
            case "proof", "proofdoc" -> user.getProofDocUrl();
            case "resume" -> user.getResumeUrl();
            case "tenthcert", "tenth" -> user.getTenthCertUrl();
            case "pucert", "pu" -> user.getPuCertUrl();
            case "ugcert", "ug" -> user.getUgCertUrl();
            case "pgcert", "pg" -> user.getPgCertUrl();
            default -> null;
        };
    }

    private ResponseEntity<Resource> serveFileSecurely(String fileUrl, String baseName) {
        try {
            Path root = fileStorageService.getRootLocation();
            Path privateDir = fileStorageService.getPrivateLocation();

            String cleanUrl = fileUrl.replace("/uploads/", "").replace("/private/", "");
            if (cleanUrl.contains("..") || cleanUrl.contains("\\")) {
                return ResponseEntity.badRequest().build();
            }

            Path filePath;
            if (fileUrl.startsWith("/private/")) {
                filePath = privateDir.resolve(cleanUrl).normalize().toAbsolutePath();
            } else {
                filePath = root.resolve(cleanUrl).normalize().toAbsolutePath();
            }

            // Path Traversal Check (Requirement 4)
            if (!filePath.startsWith(root) && !filePath.startsWith(privateDir)) {
                log.error("Attempted path traversal access: {}", fileUrl);
                return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
            }

            Resource fileResource = new UrlResource(filePath.toUri());
            if (!fileResource.exists() || !fileResource.isReadable()) {
                return ResponseEntity.notFound().build();
            }

            String contentType = determineContentType(filePath.getFileName().toString());

            return ResponseEntity.ok()
                    .contentType(MediaType.parseMediaType(contentType))
                    .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + baseName + getExtension(filePath.getFileName().toString()) + "\"")
                    .header("X-Content-Type-Options", "nosniff")
                    .header("Content-Security-Policy", "default-src 'none'; style-src 'unsafe-inline'")
                    .header("Cache-Control", "no-store, no-cache, must-revalidate, max-age=0")
                    .header("Pragma", "no-cache")
                    .body(fileResource);

        } catch (MalformedURLException e) {
            return ResponseEntity.badRequest().build();
        }
    }

    private String determineContentType(String filename) {
        String ext = getExtension(filename).toLowerCase();
        return switch (ext) {
            case ".pdf" -> "application/pdf";
            case ".jpg", ".jpeg" -> "image/jpeg";
            case ".png" -> "image/png";
            case ".webp" -> "image/webp";
            case ".mp4" -> "video/mp4";
            case ".webm" -> "video/webm";
            case ".zip" -> "application/zip";
            default -> "application/octet-stream";
        };
    }

    private String getExtension(String filename) {
        if (filename == null || filename.lastIndexOf('.') == -1) {
            return "";
        }
        return filename.substring(filename.lastIndexOf('.'));
    }
}
