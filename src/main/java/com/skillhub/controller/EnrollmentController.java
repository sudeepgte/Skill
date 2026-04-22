package com.skillhub.controller;

import com.skillhub.model.*;
import com.skillhub.service.EnrollmentService;
import com.skillhub.service.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.Map;

@RestController
@RequestMapping("/api/enrollments")
public class EnrollmentController {

    @Autowired
    private EnrollmentService enrollmentService;

    @Autowired
    private com.skillhub.repository.EnrollmentRepository enrollmentRepository;

    @Autowired
    private UserService userService;

    @PostMapping("/{courseId}")
    public ResponseEntity<?> enrollUser(@PathVariable("courseId") Long courseId, Principal principal) {
        if (principal == null) {
            return ResponseEntity.status(401).body("Unauthorized");
        }
        User user = userService.findByEmail(principal.getName());
        enrollmentService.enrollUser(user, courseId);
        return ResponseEntity.ok(Map.of("message", "Enrolled successfully"));
    }

    @PostMapping("/progress/{lectureId}/complete")
    public ResponseEntity<?> markLectureComplete(@PathVariable("lectureId") Long lectureId, Principal principal) {
        if (principal == null) {
            return ResponseEntity.status(401).body("Unauthorized");
        }
        User user = userService.findByEmail(principal.getName());
        enrollmentService.markLectureAsCompleted(user, lectureId);
        return ResponseEntity.ok(Map.of("message", "Progress updated"));
    }

    @PostMapping("/progress/{lectureId}/incomplete")
    public ResponseEntity<?> markLectureIncomplete(@PathVariable("lectureId") Long lectureId, Principal principal) {
        if (principal == null) {
            return ResponseEntity.status(401).body("Unauthorized");
        }
        User user = userService.findByEmail(principal.getName());
        enrollmentService.markLectureAsIncomplete(user, lectureId);
        return ResponseEntity.ok(Map.of("message", "Progress updated"));
    }

    /**
     * Sync endpoint: marks ALL lectures in a course as complete.
     * Called from certifications page when localStorage shows 100% but DB shows 0%.
     */
    @PostMapping("/sync/{courseId}")
    public ResponseEntity<?> syncCourseProgress(@PathVariable("courseId") Long courseId, Principal principal) {
        if (principal == null) {
            return ResponseEntity.status(401).body("Unauthorized");
        }
        try {
            User user = userService.findByEmail(principal.getName());
            enrollmentService.markAllLecturesComplete(user, courseId);
            return ResponseEntity.ok(Map.of("message", "Progress synced"));
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/last-played/{courseId}/{lectureId}")
    public ResponseEntity<?> updateLastPlayed(@PathVariable("courseId") Long courseId,
            @PathVariable("lectureId") Long lectureId, Principal principal) {
        if (principal == null) {
            return ResponseEntity.status(401).body("Unauthorized");
        }
        try {
            User user = userService.findByEmail(principal.getName());
            enrollmentService.updateLastPlayed(user, courseId, lectureId);
            return ResponseEntity.ok(Map.of("success", true));
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * Poke endpoint: returns current progress for all user's enrollments.
     * Used for real-time polling on the profile page.
     */
    @GetMapping("/progress/pokes")
    public ResponseEntity<?> getAllProgress(Principal principal) {
        if (principal == null) {
            return ResponseEntity.status(401).body("Unauthorized");
        }
        User user = userService.findByEmail(principal.getName());
        var enrollments = enrollmentRepository.findByStudent(user);
        Map<Long, Double> progressMap = enrollments.stream()
                .filter(e -> e.getCourse() != null)
                .collect(java.util.stream.Collectors.toMap(
                        e -> e.getCourse().getId(),
                        e -> e.getProgressPercentage()));
        return ResponseEntity.ok(progressMap);
    }

    /**
     * Video progress endpoint: saves partial watch percentage to DB.
     * Called from learning room every 10s so instructor dashboard shows real-time progress.
     */
    @PostMapping("/video-progress/{courseId}")
    public ResponseEntity<?> saveVideoProgress(@PathVariable("courseId") Long courseId,
            @RequestParam("pct") double pct, Principal principal) {
        if (principal == null) {
            return ResponseEntity.status(401).body("Unauthorized");
        }
        try {
            User user = userService.findByEmail(principal.getName());
            enrollmentService.updateVideoProgress(user, courseId, pct);
            return ResponseEntity.ok(Map.of("message", "Progress saved"));
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of("error", e.getMessage()));
        }
    }
}
