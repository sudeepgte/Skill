package com.skillhub.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import java.io.File;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class VideoController {

    @PostMapping("/upload-video")
    public ResponseEntity<?> uploadVideo(@RequestParam("file") MultipartFile file) {
        try {
            // Using a relative path for now, or could use the configured Property
            // But user asked for specific "uploads/videos/" folder.
            String uploadDir = "uploads/videos/";
            File directory = new File(uploadDir);
            if (!directory.exists()) {
                directory.mkdirs();
            }

            String fileName = System.currentTimeMillis() + "_" + file.getOriginalFilename();
            file.transferTo(new File(directory.getAbsolutePath() + File.separator + fileName));

            return ResponseEntity.ok(Map.of("success", true, "fileName", fileName));
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(500).body(Map.of("success", false, "error", e.getMessage()));
        }
    }
}
