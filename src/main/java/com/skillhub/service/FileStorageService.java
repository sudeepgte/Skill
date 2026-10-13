package com.skillhub.service;

import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.multipart.MultipartFile;
import jakarta.annotation.PostConstruct;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.UUID;

@Service
public class FileStorageService {

    private final Path rootLocation;

    @Autowired
    public FileStorageService(
            @org.springframework.beans.factory.annotation.Value("${file.upload-dir}") String uploadDir) {
        this.rootLocation = Paths.get(uploadDir);
    }

    @PostConstruct
    public void init() {
        try {
            Files.createDirectories(rootLocation);
        } catch (IOException e) {
            throw new RuntimeException("Could not initialize storage", e);
        }
    }

    public String store(MultipartFile file) {
        try {
            if (file.isEmpty()) {
                throw new RuntimeException("Failed to store empty file.");
            }
            String rawOriginal = file.getOriginalFilename() != null ? file.getOriginalFilename() : "file";
            String cleanName = org.springframework.util.StringUtils.cleanPath(rawOriginal);
            String sanitized = cleanName.replaceAll("[^a-zA-Z0-9._-]", "_");
            if (sanitized.isEmpty() || sanitized.startsWith(".")) {
                sanitized = "file" + sanitized;
            }

            System.out.println("STORING FILE: " + sanitized + " (" + file.getSize() + " bytes)");
            String filename = UUID.randomUUID().toString() + "_" + sanitized;
            Files.copy(file.getInputStream(), this.rootLocation.resolve(filename), StandardCopyOption.REPLACE_EXISTING);
            String savedPath = "/uploads/" + filename;
            System.out.println("FILE SAVED AT: " + savedPath);
            return savedPath;
        } catch (IOException e) {
            System.err.println("STORAGE ERROR: " + e.getMessage());
            throw new RuntimeException("Failed to store file", e);
        }
    }

    public void delete(String fileUrl) {
        if (fileUrl == null || fileUrl.isEmpty()) return;
        try {
            String filename = fileUrl.replace("/uploads/", "");
            Path filePath = this.rootLocation.resolve(filename);
            Files.deleteIfExists(filePath);
            System.out.println("FILE DELETED: " + fileUrl);
        } catch (IOException e) {
            System.err.println("COULD NOT DELETE FILE: " + fileUrl + " -> " + e.getMessage());
        }
    }
}
