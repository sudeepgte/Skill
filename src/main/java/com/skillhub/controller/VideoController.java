package com.skillhub.controller;

import com.skillhub.model.User;
import com.skillhub.service.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.security.Principal;
import java.util.Map;
import java.util.Set;

@RestController
@RequestMapping("/api")
public class VideoController {

    private static final Set<String> ALLOWED_EXTENSIONS = Set.of(".mp4", ".webm");
    private static final Set<String> ALLOWED_CONTENT_TYPES = Set.of("video/mp4", "video/webm", "application/octet-stream");

    @Autowired
    private UserService userService;

    @PostMapping("/upload-video")
    public ResponseEntity<?> uploadVideo(@RequestParam("file") MultipartFile file, Principal principal) {
        // 1. Role Verification (Only authorized Instructors and Admins - BUG-018)
        if (principal == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("success", false, "error", "Unauthorized: Authentication required"));
        }

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        boolean isAuthorized = false;
        if (auth != null) {
            isAuthorized = auth.getAuthorities().stream().anyMatch(a -> {
                String role = a.getAuthority().toUpperCase();
                return role.equals("INSTRUCTOR") || role.equals("ROLE_INSTRUCTOR") ||
                       role.equals("ADMIN") || role.equals("ROLE_ADMIN");
            });
        }

        if (!isAuthorized) {
            User user = userService.findByEmail(principal.getName());
            if (user != null && ("INSTRUCTOR".equalsIgnoreCase(user.getRole()) || "ADMIN".equalsIgnoreCase(user.getRole()))) {
                isAuthorized = true;
            }
        }

        if (!isAuthorized) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("success", false, "error", "Forbidden: Only authorized instructors and admins can upload video files"));
        }

        // 2. File Verification (Extension and Media Whitelist - BUG-018)
        if (file == null || file.isEmpty()) {
            return ResponseEntity.badRequest()
                    .body(Map.of("success", false, "error", "File is required and cannot be empty"));
        }

        String rawOriginal = file.getOriginalFilename() != null ? file.getOriginalFilename() : "";
        String cleanName = StringUtils.cleanPath(rawOriginal);
        if (cleanName.contains("..") || cleanName.contains("/") || cleanName.contains("\\")) {
            return ResponseEntity.badRequest()
                    .body(Map.of("success", false, "error", "Invalid file name"));
        }

        int dotIndex = cleanName.lastIndexOf('.');
        if (dotIndex == -1) {
            return ResponseEntity.badRequest()
                    .body(Map.of("success", false, "error", "File must have a valid extension (.mp4, .webm)"));
        }

        String extension = cleanName.substring(dotIndex).toLowerCase();
        if (!ALLOWED_EXTENSIONS.contains(extension)) {
            return ResponseEntity.badRequest()
                    .body(Map.of("success", false, "error", "Invalid file type. Only verified media files (.mp4, .webm) are allowed"));
        }

        String contentType = file.getContentType();
        if (contentType != null && !contentType.trim().isEmpty()) {
            String lowerContentType = contentType.toLowerCase().trim();
            if (!lowerContentType.startsWith("video/") && !ALLOWED_CONTENT_TYPES.contains(lowerContentType)) {
                return ResponseEntity.badRequest()
                        .body(Map.of("success", false, "error", "Invalid media content type: " + contentType));
            }
        }

        try {
            String uploadDir = "uploads/videos/";
            File directory = new File(uploadDir);
            if (!directory.exists()) {
                directory.mkdirs();
            }

            String sanitized = cleanName.replaceAll("[^a-zA-Z0-9._-]", "_");
            String fileName = System.currentTimeMillis() + "_" + sanitized;
            file.transferTo(new File(directory.getAbsolutePath() + File.separator + fileName));

            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "fileName", fileName,
                    "fileUrl", "/uploads/videos/" + fileName
            ));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("success", false, "error", "File upload failed: " + e.getMessage()));
        }
    }
}
