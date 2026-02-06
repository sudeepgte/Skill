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

    @PostMapping("/add")
    public String addReview(@RequestParam Long courseId,
            @RequestParam int rating,
            @RequestParam String comment,
            Principal principal,
            RedirectAttributes redirectAttributes) {

        if (principal == null) {
            return "redirect:/login";
        }

        User user = userService.findByEmail(principal.getName());
        Course course = courseService.getCourseById(courseId);

        if (course != null) {
            Review review = new Review();
            review.setCourse(course);
            review.setUser(user);
            review.setRating(rating);
            review.setComment(comment);
            review.setCreatedAt(LocalDateTime.now());

            reviewRepository.save(review);
            redirectAttributes.addFlashAttribute("success", "Review submitted successfully!");
        }

        return "redirect:/course/" + courseId;
    }
}
