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
            System.out.println("STORING FILE: " + file.getOriginalFilename() + " (" + file.getSize() + " bytes)");
            String filename = UUID.randomUUID().toString() + "_" + file.getOriginalFilename();
            Files.copy(file.getInputStream(), this.rootLocation.resolve(filename), StandardCopyOption.REPLACE_EXISTING);
            String savedPath = "/uploads/" + filename;
            System.out.println("FILE SAVED AT: " + savedPath);
            return savedPath;
        } catch (IOException e) {
            System.err.println("STORAGE ERROR: " + e.getMessage());
            throw new RuntimeException("Failed to store file " + file.getOriginalFilename(), e);
        }
    }
}
