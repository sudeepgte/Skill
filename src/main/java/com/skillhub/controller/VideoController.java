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

        User currentUser = userService.findByEmail(principal.getName());
        if (currentUser == null || currentUser.isBlocked() || !Boolean.TRUE.equals(currentUser.isApproved())) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("success", false, "error", "Forbidden: Account is not approved or is blocked"));
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
            if ("INSTRUCTOR".equalsIgnoreCase(currentUser.getRole()) || "ADMIN".equalsIgnoreCase(currentUser.getRole())) {
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
                    .body(Map.of("success", false, "error", "File is empty or missing"));
        }

        String originalFilename = StringUtils.cleanPath(file.getOriginalFilename() != null ? file.getOriginalFilename() : "");
        String extension = "";
        int dotIndex = originalFilename.lastIndexOf('.');
        if (dotIndex > 0) {
            extension = originalFilename.substring(dotIndex).toLowerCase();
        }

        if (!ALLOWED_EXTENSIONS.contains(extension)) {
            return ResponseEntity.badRequest()
                    .body(Map.of("success", false, "error", "Unsupported file type. Only MP4 and WebM videos are permitted."));
        }

        String contentType = file.getContentType();
        if (contentType != null && !ALLOWED_CONTENT_TYPES.contains(contentType.toLowerCase())) {
            return ResponseEntity.badRequest()
                    .body(Map.of("success", false, "error", "Invalid video MIME type. Only MP4 and WebM are allowed."));
        }

        try {
            // 3. Secure Storage
            String uploadDir = "uploads/videos";
            File dir = new File(uploadDir);
            if (!dir.exists()) {
                dir.mkdirs();
            }

            String uniqueName = System.currentTimeMillis() + "_" + originalFilename.replaceAll("[^a-zA-Z0-9.-]", "_");
            File destination = new File(dir, uniqueName);
            file.transferTo(destination.toPath());

            String videoUrl = "/uploads/videos/" + uniqueName;
            return ResponseEntity.ok(Map.of("success", true, "url", videoUrl, "fileName", uniqueName));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("success", false, "error", "Failed to upload video: " + e.getMessage()));
        }
    }
}
