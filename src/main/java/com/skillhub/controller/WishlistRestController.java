package com.skillhub.controller;

import com.skillhub.model.Course;
import com.skillhub.model.CourseStatus;
import com.skillhub.model.User;
import com.skillhub.model.Wishlist;
import com.skillhub.repository.WishlistRepository;
import com.skillhub.service.CourseService;
import com.skillhub.service.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
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

    private Long parseCourseId(Map<String, Object> bodyPayload) {
        if (bodyPayload != null && bodyPayload.get("courseId") != null) {
            Object raw = bodyPayload.get("courseId");
            if (raw instanceof Number) {
                return ((Number) raw).longValue();
            } else {
                try {
                    return Long.parseLong(String.valueOf(raw).trim());
                } catch (Exception ignored) {}
            }
        }
        return null;
    }

    private ResponseEntity<?> processAddToWishlist(Long courseId, Principal principal) {
        if (principal == null) {
            return ResponseEntity.status(401).body(Map.of("success", false, "message", "User not logged in"));
        }

        if (courseId == null) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", "Course ID is required"));
        }

        String email = principal.getName();
        User user = userService.findByEmail(email);
        Course course = courseService.getCourseById(courseId);

        if (course == null) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", "Course not found"));
        }
        if (course.getStatus() != CourseStatus.PUBLISHED || !course.isPublished()) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", "Course is not published"));
        }

        if (wishlistRepository.findByUserAndCourse(user, course).isPresent()) {
            return ResponseEntity.ok(Map.of("success", true, "alreadyAdded", true, "alreadyInWishlist", true, "message", "Course already in wishlist"));
        }

        try {
            Wishlist wishlist = new Wishlist(user, course);
            wishlistRepository.save(wishlist);
        } catch (DataIntegrityViolationException ex) {
            return ResponseEntity.ok(Map.of("success", true, "alreadyAdded", true, "alreadyInWishlist", true, "message", "Course already in wishlist"));
        }

        return ResponseEntity.ok(Map.of("success", true, "alreadyAdded", false, "alreadyInWishlist", false, "message", "Course added to wishlist"));
    }

    private ResponseEntity<?> processRemoveFromWishlist(Long courseId, Principal principal) {
        if (principal == null) {
            return ResponseEntity.status(401).body(Map.of("success", false, "message", "User not logged in"));
        }

        if (courseId == null) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", "Course ID is required"));
        }

        String email = principal.getName();
        User user = userService.findByEmail(email);
        Course course = courseService.getCourseById(courseId);

        if (course != null && user != null) {
            wishlistRepository.findByUserAndCourse(user, course).ifPresent(wishlistRepository::delete);
        }

        return ResponseEntity.ok(Map.of("success", true, "message", "Course removed from wishlist"));
    }

    /**
     * Supports JSON payload.
     */
    @PostMapping(value = "/add", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> addToWishlistJson(
            @RequestBody(required = false) Map<String, Object> bodyPayload,
            Principal principal) {
        Long courseId = parseCourseId(bodyPayload);
        return processAddToWishlist(courseId, principal);
    }

    /**
     * Supports form-urlencoded parameter.
     */
    @PostMapping(value = "/add", consumes = "!application/json")
    public ResponseEntity<?> addToWishlistForm(
            @RequestParam(value = "courseId", required = false) Long courseId,
            Principal principal) {
        return processAddToWishlist(courseId, principal);
    }

    /**
     * REST API for removing a course from the student's wishlist via JSON payload.
     */
    @PostMapping(value = "/remove", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> removeFromWishlistJson(
            @RequestBody(required = false) Map<String, Object> bodyPayload,
            Principal principal) {
        Long courseId = parseCourseId(bodyPayload);
        return processRemoveFromWishlist(courseId, principal);
    }

    /**
     * REST API for removing a course from the student's wishlist via form parameter.
     */
    @PostMapping(value = "/remove", consumes = "!application/json")
    public ResponseEntity<?> removeFromWishlistForm(
            @RequestParam(value = "courseId", required = false) Long courseId,
            Principal principal) {
        return processRemoveFromWishlist(courseId, principal);
    }
}
