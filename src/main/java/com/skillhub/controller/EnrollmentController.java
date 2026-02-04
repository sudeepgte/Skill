package com.skillhub.controller;

import com.skillhub.model.User;
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
    private UserService userService;

    @PostMapping("/{courseId}")
    public ResponseEntity<?> enrollUser(@PathVariable Long courseId, Principal principal) {
        if (principal == null) {
            return ResponseEntity.status(401).body("Unauthorized");
        }
        User user = userService.findByEmail(principal.getName());
        enrollmentService.enrollUser(user, courseId);
        return ResponseEntity.ok(Map.of("message", "Enrolled successfully"));
    }

    @PostMapping("/progress/{lectureId}/complete")
    public ResponseEntity<?> markLectureComplete(@PathVariable Long lectureId, Principal principal) {
        if (principal == null) {
            return ResponseEntity.status(401).body("Unauthorized");
        }
        User user = userService.findByEmail(principal.getName());
        enrollmentService.markLectureAsCompleted(user, lectureId);
        return ResponseEntity.ok(Map.of("message", "Progress updated"));
    }
}
