package com.skillhub.controller;

import com.skillhub.model.Course;
import com.skillhub.model.User;
import com.skillhub.model.Wishlist;
import com.skillhub.repository.WishlistRepository;
import com.skillhub.service.CourseService;
import com.skillhub.service.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/wishlist")
public class WishlistRestController {

    @Autowired
    private WishlistRepository wishlistRepository;

    @Autowired
    private UserService userService;

    @Autowired
    private CourseService courseService;

    @PostMapping("/add")
    public ResponseEntity<?> addToWishlist(@RequestBody Map<String, Long> payload, Principal principal) {
        if (principal == null) {
            return ResponseEntity.status(401).body(Map.of("success", false, "message", "User not logged in"));
        }

        Long courseId = payload.get("courseId");
        if (courseId == null) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", "Course ID is required"));
        }

        String email = principal.getName();
        User user = userService.findByEmail(email);
        Course course = courseService.getCourseById(courseId);

        if (course == null) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", "Course not found"));
        }
        if (course.getStatus() != com.skillhub.model.CourseStatus.PUBLISHED || !course.isPublished()) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", "Course is not published"));
        }

        if (wishlistRepository.findByUserAndCourse(user, course).isPresent()) {
            return ResponseEntity.ok(Map.of("success", true, "message", "Course already in wishlist"));
        }

        Wishlist wishlist = new Wishlist(user, course);
        wishlistRepository.save(wishlist);

        return ResponseEntity.ok(Map.of("success", true, "message", "Course added to wishlist"));
    }
}
