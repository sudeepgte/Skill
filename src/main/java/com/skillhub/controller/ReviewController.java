package com.skillhub.controller;

import com.skillhub.model.Course;
import com.skillhub.model.Review;
import com.skillhub.model.User;
import com.skillhub.service.CourseService;
import com.skillhub.service.UserService;
import com.skillhub.repository.ReviewRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.security.Principal;
import java.time.LocalDateTime;

@Controller
@RequestMapping("/reviews")
public class ReviewController {

    @Autowired
    private ReviewRepository reviewRepository;

    @Autowired
    private CourseService courseService;

    @Autowired
    private UserService userService;

    @Autowired
    private com.skillhub.service.EnrollmentService enrollmentService;

    @PostMapping("/add")
    public String addReview(@RequestParam("courseId") Long courseId,
            @RequestParam("rating") int rating,
            @RequestParam("comment") String comment,
            Principal principal,
            RedirectAttributes redirectAttributes) {

        if (principal == null) {
            return "redirect:/login";
        }

        User user = userService.findByEmail(principal.getName());
        if (user == null) {
            return "redirect:/login";
        }

        Course course = courseService.getCourseById(courseId);
        if (course == null) {
            redirectAttributes.addFlashAttribute("error", "Course not found.");
            return "redirect:/home";
        }

        // Business Logic Validation: Only enrolled students can review a course
        if (!enrollmentService.isEnrolled(user, course)) {
            redirectAttributes.addFlashAttribute("error", "Only students who have enrolled in this course are permitted to rate and review it.");
            return "redirect:/course/" + courseId;
        }

        // Rating validation
        if (rating < 1 || rating > 5) {
            redirectAttributes.addFlashAttribute("error", "Rating must be between 1 and 5 stars.");
            return "redirect:/course/" + courseId;
        }

        Review review = new Review();
        review.setCourse(course);
        review.setUser(user);
        review.setRating(rating);
        review.setComment(comment != null ? comment.trim() : "");
        review.setCreatedAt(LocalDateTime.now());

        reviewRepository.save(review);
        redirectAttributes.addFlashAttribute("success", "Review submitted successfully!");

        return "redirect:/course/" + courseId;
    }
}
