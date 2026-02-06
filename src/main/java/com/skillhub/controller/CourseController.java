package com.skillhub.controller;

import com.skillhub.service.CourseService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;

import com.skillhub.dto.CourseDTO;
import com.skillhub.mapper.CourseMapper;
import org.springframework.web.bind.annotation.RequestParam;
import java.security.Principal;

@Controller
@RequestMapping("/course")
public class CourseController {

    @Autowired
    private CourseService courseService;

    @Autowired
    private CourseMapper courseMapper;

    @Autowired
    private com.skillhub.repository.ReviewRepository reviewRepository;

    @GetMapping("/{id}")
    public String getCourseDetails(@PathVariable Long id, Model model) {
        // Fetch entity
        com.skillhub.model.Course course = courseService.getCourseById(id);

        // Map to DTO
        CourseDTO courseDTO = courseMapper.toDTO(course);

        // Fetch reviews
        java.util.List<com.skillhub.model.Review> reviews = reviewRepository
                .findByCourse(course, org.springframework.data.domain.Pageable.unpaged()).getContent();

        model.addAttribute("course", courseDTO);
        model.addAttribute("reviews", reviews);
        return "course-details";
    }

    @GetMapping("/{id}/learn")
    public String getLearningRoom(@PathVariable Long id,
            @RequestParam(required = false) Long lectureId,
            Model model,
            Principal principal) {
        if (principal == null) {
            return "redirect:/login";
        }

        // Logic to verify enrollment would go here (or in service)
        // For now assuming enrolled or public

        com.skillhub.model.Course course = courseService.getCourseById(id);
        CourseDTO courseDTO = courseMapper.toDTO(course);

        // Use provided lectureId or find first/last played
        // This logic should ideally be in a service
        Long targetLectureId = lectureId;
        if (targetLectureId == null) {
            // Default to first lecture of first section
            if (!course.getSections().isEmpty() && !course.getSections().get(0).getLectures().isEmpty()) {
                targetLectureId = course.getSections().get(0).getLectures().get(0).getId();
            }
        }

        // Pass current lesson for titles/video
        // We'll need to find the specific lesson object in the DTO or Entity
        // Simplification: Iterate to find it
        com.skillhub.dto.LectureDTO currentLesson = null;
        if (targetLectureId != null) {
            for (com.skillhub.dto.SectionDTO section : courseDTO.getSections()) {
                for (com.skillhub.dto.LectureDTO lecture : section.getLectures()) {
                    if (lecture.getId().equals(targetLectureId)) {
                        currentLesson = lecture;
                        break;
                    }
                }
            }
        }

        model.addAttribute("course", courseDTO);
        model.addAttribute("currentLesson", currentLesson);

        return "course/learning_room";
    }

    @org.springframework.beans.factory.annotation.Autowired
    private com.skillhub.repository.WishlistRepository wishlistRepository;

    @org.springframework.beans.factory.annotation.Autowired
    private com.skillhub.service.UserService userService;

    @org.springframework.web.bind.annotation.PostMapping("/wishlist/add")
    public String addToWishlist(@RequestParam Long courseId, Principal principal,
            org.springframework.web.servlet.mvc.support.RedirectAttributes redirectAttributes) {
        if (principal == null) {
            return "redirect:/login";
        }
        String email = principal.getName();
        com.skillhub.model.User user = userService.findByEmail(email);
        com.skillhub.model.Course course = courseService.getCourseById(courseId);

        if (course != null && wishlistRepository.findByUserAndCourse(user, course).isEmpty()) {
            com.skillhub.model.Wishlist wishlist = new com.skillhub.model.Wishlist(user, course);
            wishlistRepository.save(wishlist);
            redirectAttributes.addFlashAttribute("success", "Added to wishlist!");
        }

        // return "redirect:/course/" + courseId;
        // For better UX, redirect to where they came from or Wishlist
        return "redirect:/student/wishlist";
    }

    @org.springframework.web.bind.annotation.PostMapping("/wishlist/remove")
    public String removeFromWishlist(@RequestParam Long courseId, Principal principal) {
        if (principal == null) {
            return "redirect:/login";
        }
        String email = principal.getName();
        com.skillhub.model.User user = userService.findByEmail(email);
        com.skillhub.model.Course course = courseService.getCourseById(courseId);

        wishlistRepository.findByUserAndCourse(user, course).ifPresent(wishlistRepository::delete);

        return "redirect:/student/wishlist";
    }
}
