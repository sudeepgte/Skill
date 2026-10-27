package com.skillhub.service;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.*;

@Service
public class FileStorageService {

    private static final Logger log = LoggerFactory.getLogger(FileStorageService.class);

    public enum FileCategory {
        IDENTITY_DOCUMENT,
        IMAGE,
        VIDEO,
        RESOURCE
    }

    private final Path rootLocation;
    private final Path publicLocation;
    private final Path privateLocation;

    private static final long MAX_DOCUMENT_SIZE = 10 * 1024 * 1024; // 10MB
    private static final long MAX_IMAGE_SIZE = 10 * 1024 * 1024;    // 10MB
    private static final long MAX_VIDEO_SIZE = 100 * 1024 * 1024;   // 100MB
    private static final long MAX_RESOURCE_SIZE = 50 * 1024 * 1024; // 50MB

    private static final Set<String> ALLOWED_DOC_EXTENSIONS = Set.of(".pdf", ".jpg", ".jpeg", ".png");
    private static final Set<String> ALLOWED_IMAGE_EXTENSIONS = Set.of(".jpg", ".jpeg", ".png", ".webp");
    private static final Set<String> ALLOWED_VIDEO_EXTENSIONS = Set.of(".mp4", ".webm");
    private static final Set<String> ALLOWED_RESOURCE_EXTENSIONS = Set.of(".pdf", ".zip", ".doc", ".docx", ".txt", ".csv", ".png", ".jpg", ".jpeg");

    private static final Set<String> PROHIBITED_EXTENSIONS = Set.of(
            ".exe", ".bat", ".cmd", ".sh", ".php", ".phtml", ".jsp", ".jspx",
            ".jar", ".war", ".ear", ".py", ".pyw", ".js", ".vbs", ".ps1",
            ".svg", ".html", ".htm", ".xhtml", ".cgi", ".pl", ".dll", ".so"
    );

    @Autowired
    public FileStorageService(@Value("${file.upload-dir:uploads}") String uploadDir) {
        this.rootLocation = Paths.get(uploadDir).toAbsolutePath().normalize();
        this.publicLocation = this.rootLocation.resolve("public").normalize();
        this.privateLocation = this.rootLocation.resolve("private").normalize();
    }

    @PostConstruct
    public void init() {
        try {
            Files.createDirectories(this.rootLocation);
            Files.createDirectories(this.publicLocation);
            Files.createDirectories(this.privateLocation);
            Files.createDirectories(this.privateLocation.resolve("documents"));
            Files.createDirectories(this.privateLocation.resolve("exam-recordings"));
        } catch (IOException e) {
            log.error("Could not initialize file storage directories", e);
            throw new RuntimeException("Could not initialize storage", e);
        }
    }

    public Path getRootLocation() {
        return rootLocation;
    }

    public Path getPrivateLocation() {
        return privateLocation;
    }

    public Path getPublicLocation() {
        return publicLocation;
    }

    /**
     * Stores private verification documents (Aadhaar, government proof, resume, educational certificates)
     * in private storage outside public web directories.
     */
    public String storePrivateDocument(MultipartFile file, String subfolder) {
        validateFile(file, FileCategory.IDENTITY_DOCUMENT, MAX_DOCUMENT_SIZE, ALLOWED_DOC_EXTENSIONS);
        scanForMaliciousPayloads(file);

        String extension = getFileExtension(file.getOriginalFilename());
        String serverAssignedName = UUID.randomUUID().toString() + extension;

        try {
            Path targetDir = this.privateLocation.resolve("documents").resolve(sanitizePathPart(subfolder)).normalize();
            Files.createDirectories(targetDir);

            Path destinationFile = targetDir.resolve(serverAssignedName).normalize().toAbsolutePath();
            if (!destinationFile.startsWith(this.privateLocation)) {
                throw new SecurityException("Cannot store file outside private storage directory.");
            }

            Files.copy(file.getInputStream(), destinationFile, StandardCopyOption.REPLACE_EXISTING);
            log.info("Stored private document in subfolder {} with server-assigned id", subfolder);
            return "/private/documents/" + sanitizePathPart(subfolder) + "/" + serverAssignedName;
        } catch (IOException e) {
            log.error("Failed to store private document", e);
            throw new RuntimeException("Failed to store private document", e);
        }
    }

    /**
     * Stores public assets (profile images, course thumbnails).
     */
    public String storePublic(MultipartFile file, String subfolder) {
        validateFile(file, FileCategory.IMAGE, MAX_IMAGE_SIZE, ALLOWED_IMAGE_EXTENSIONS);
        scanForMaliciousPayloads(file);

        String extension = getFileExtension(file.getOriginalFilename());
        String serverAssignedName = UUID.randomUUID().toString() + extension;

        try {
            Path targetDir = this.publicLocation.resolve(sanitizePathPart(subfolder)).normalize();
            Files.createDirectories(targetDir);

            Path destinationFile = targetDir.resolve(serverAssignedName).normalize().toAbsolutePath();
            if (!destinationFile.startsWith(this.publicLocation)) {
                throw new SecurityException("Cannot store file outside public storage directory.");
            }

            Files.copy(file.getInputStream(), destinationFile, StandardCopyOption.REPLACE_EXISTING);
            return "/uploads/public/" + sanitizePathPart(subfolder) + "/" + serverAssignedName;
        } catch (IOException e) {
            log.error("Failed to store public file", e);
            throw new RuntimeException("Failed to store public file", e);
        }
    }

    /**
     * Legacy store method with full hardening: extension whitelisting, magic byte validation,
     * path traversal blocking, and server-controlled naming.
     */
    public String store(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("Failed to store empty file.");
        }

        String rawOriginal = file.getOriginalFilename() != null ? file.getOriginalFilename() : "file";
        String extension = getFileExtension(rawOriginal);

        // Reject dangerous executable extensions
        if (PROHIBITED_EXTENSIONS.contains(extension.toLowerCase())) {
            throw new SecurityException("Uploading executable or script files is strictly prohibited: " + extension);
        }

        // Determine category and enforce limits
        if (ALLOWED_VIDEO_EXTENSIONS.contains(extension.toLowerCase())) {
            validateFile(file, FileCategory.VIDEO, MAX_VIDEO_SIZE, ALLOWED_VIDEO_EXTENSIONS);
        } else if (ALLOWED_IMAGE_EXTENSIONS.contains(extension.toLowerCase())) {
            validateFile(file, FileCategory.IMAGE, MAX_IMAGE_SIZE, ALLOWED_IMAGE_EXTENSIONS);
        } else if (ALLOWED_DOC_EXTENSIONS.contains(extension.toLowerCase())) {
            validateFile(file, FileCategory.IDENTITY_DOCUMENT, MAX_DOCUMENT_SIZE, ALLOWED_DOC_EXTENSIONS);
        } else if (ALLOWED_RESOURCE_EXTENSIONS.contains(extension.toLowerCase())) {
            validateFile(file, FileCategory.RESOURCE, MAX_RESOURCE_SIZE, ALLOWED_RESOURCE_EXTENSIONS);
        } else {
            throw new SecurityException("File extension not permitted: " + extension);
        }

        scanForMaliciousPayloads(file);

        try {
            String filename = UUID.randomUUID().toString() + extension;
            Path destinationFile = this.rootLocation.resolve(filename).normalize().toAbsolutePath();
            if (!destinationFile.startsWith(this.rootLocation)) {
                throw new SecurityException("Cannot store file outside target upload directory.");
            }

            Files.copy(file.getInputStream(), destinationFile, StandardCopyOption.REPLACE_EXISTING);
            return "/uploads/" + filename;
        } catch (IOException e) {
            log.error("Failed to store file", e);
            throw new RuntimeException("Failed to store file", e);
        }
    }

    /**
     * Safe file deletion preventing path traversal.
     */
    public void delete(String fileUrl) {
        if (fileUrl == null || fileUrl.trim().isEmpty()) return;
        try {
            String clean = fileUrl.replace("/uploads/", "").replace("/private/", "");
            if (clean.contains("..") || clean.contains("\\")) {
                throw new SecurityException("Path traversal attempt in file deletion.");
            }

            Path target;
            if (fileUrl.startsWith("/private/")) {
                target = this.privateLocation.resolve(clean).normalize().toAbsolutePath();
                if (!target.startsWith(this.privateLocation)) {
                    throw new SecurityException("Cannot delete outside private directory.");
                }
            } else {
                target = this.rootLocation.resolve(clean).normalize().toAbsolutePath();
                if (!target.startsWith(this.rootLocation)) {
                    throw new SecurityException("Cannot delete outside root directory.");
                }
            }

            Files.deleteIfExists(target);
            log.info("Safely deleted file reference");
        } catch (IOException e) {
            log.warn("Could not delete file {}: {}", fileUrl, e.getMessage());
        }
    }

    private void validateFile(MultipartFile file, FileCategory category, long maxSize, Set<String> allowedExtensions) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("Cannot upload empty file.");
        }

        if (file.getSize() > maxSize) {
            throw new IllegalArgumentException("File size (" + file.getSize() + " bytes) exceeds maximum permitted limit (" + maxSize + " bytes).");
        }

        String rawOriginal = file.getOriginalFilename() != null ? file.getOriginalFilename() : "";
        String extension = getFileExtension(rawOriginal);

        if (!allowedExtensions.contains(extension.toLowerCase())) {
            throw new SecurityException("File extension " + extension + " is not allowed for category " + category);
        }

        // Magic byte verification for high-fidelity formats
        verifyFileSignature(file, extension);
    }

    private void verifyFileSignature(MultipartFile file, String extension) {
        try (InputStream is = file.getInputStream()) {
            byte[] header = new byte[8];
            int read = is.read(header);
            if (read < 3) return;

            String ext = extension.toLowerCase();
            if (ext.equals(".pdf")) {
                // PDF header: %PDF (0x25 0x50 0x44 0x46)
                if (header[0] != 0x25 || header[1] != 0x50 || header[2] != 0x44 || header[3] != 0x46) {
                    throw new SecurityException("File header does not match valid PDF signature.");
                }
            } else if (ext.equals(".jpg") || ext.equals(".jpeg")) {
                // JPEG header: FF D8 FF
                if ((header[0] & 0xFF) != 0xFF || (header[1] & 0xFF) != 0xD8 || (header[2] & 0xFF) != 0xFF) {
                    throw new SecurityException("File header does not match valid JPEG signature.");
                }
            } else if (ext.equals(".png")) {
                // PNG header: 89 50 4E 47
                if ((header[0] & 0xFF) != 0x89 || (header[1] & 0xFF) != 0x50 || (header[2] & 0xFF) != 0x4E || (header[3] & 0xFF) != 0x47) {
                    throw new SecurityException("File header does not match valid PNG signature.");
                }
            }
        } catch (IOException e) {
            log.warn("Could not inspect file signature: {}", e.getMessage());
        }
    }

    private void scanForMaliciousPayloads(MultipartFile file) {
        try (InputStream is = file.getInputStream()) {
            byte[] buffer = new byte[4096];
            int bytesRead = is.read(buffer);
            if (bytesRead > 0) {
                String snippet = new String(buffer, 0, bytesRead).toLowerCase();
                if (snippet.contains("<script") || snippet.contains("javascript:") || snippet.contains("<?php") || snippet.contains("<%@")) {
                    log.error("Malicious script signature detected in file upload! Rejecting file.");
                    throw new SecurityException("Malicious script content detected in uploaded file.");
                }
            }
        } catch (IOException e) {
            log.warn("Payload scan failed to read file: {}", e.getMessage());
        }
    }

    private String getFileExtension(String filename) {
        if (filename == null || filename.lastIndexOf('.') == -1) {
            return "";
        }
        return filename.substring(filename.lastIndexOf('.')).toLowerCase();
    }

    private String sanitizePathPart(String part) {
        if (part == null) return "general";
        return part.replaceAll("[^a-zA-Z0-9_-]", "");
    }
}
