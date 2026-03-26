package com.skillhub.controller;

import com.skillhub.service.CourseService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import com.skillhub.dto.CourseDTO;
import com.skillhub.mapper.CourseMapper;
import java.security.Principal;

@Controller
public class CourseController {

    @Autowired
    private CourseService courseService;

    @Autowired
    private CourseMapper courseMapper;

    @Autowired
    private com.skillhub.repository.ReviewRepository reviewRepository;

    @Autowired
    private com.skillhub.repository.EnrollmentRepository enrollmentRepository;

    @Autowired
    private com.skillhub.service.UserService userService;

    @GetMapping({ "/course/{id}", "/user/course/{id}" })
    public String getCourseDetails(@PathVariable Long id, Model model, Principal principal) {
        // Fetch entity
        com.skillhub.model.Course course = courseService.getCourseById(id);

        if (course == null) {
            return "redirect:/?error=CourseNotFound";
        }

        // Map to DTO
        CourseDTO courseDTO = courseMapper.toDTO(course);

        // Check Enrollment Status
        boolean isEnrolled = false;
        com.skillhub.model.User user = null;
        if (principal != null) {
            String email = principal.getName();
            user = userService.findByEmail(email);
            if (user != null) {
                // Corrected method name: findByStudentAndCourse
                isEnrolled = enrollmentRepository.findByStudentAndCourse(user, course).isPresent();
            }
        }

        // Fetch reviews
        java.util.List<com.skillhub.model.Review> reviews = reviewRepository
                .findByCourse(course, org.springframework.data.domain.Pageable.unpaged()).getContent();

        model.addAttribute("course", courseDTO);
        model.addAttribute("reviews", reviews);
        model.addAttribute("isEnrolled", isEnrolled);
        model.addAttribute("user", user);
        // NEW: expose login state
        model.addAttribute("isLoggedIn", principal != null);
        return "course-details";
    }

    @GetMapping({ "/course/{id}/learn", "/user/course/{id}/learn" })
    public String getLearningRoom(@PathVariable Long id,
            @RequestParam(required = false) Long lectureId,
            Model model,
            Principal principal) {
        if (principal == null) {
            return "redirect:/login";
        }

        com.skillhub.model.Course course = courseService.getCourseById(id);
        CourseDTO courseDTO = courseMapper.toDTO(course);

        // Use provided lectureId or find first/last played
        Long targetLectureId = lectureId;
        if (targetLectureId == null) {
            // Default to first lecture of first section
            if (!course.getSections().isEmpty() && !course.getSections().get(0).getLectures().isEmpty()) {
                targetLectureId = course.getSections().get(0).getLectures().get(0).getId();
            }
        }

        // Pass current lesson for titles/video
        com.skillhub.dto.LectureDTO currentLesson = null;
        if (targetLectureId != null) {
            for (com.skillhub.dto.SectionDTO section : courseDTO.getSections()) {
                for (com.skillhub.dto.LectureDTO lecture : section.getLectures()) {
                    if (lecture.getId().equals(targetLectureId)) {
                        currentLesson = lecture;
                        break;
                    }
                }
                if (currentLesson != null)
                    break;
            }
        }

        // Fallback: If no lesson found but course has a direct video URL, use it as intro
        if (currentLesson == null && courseDTO.getVideoUrl() != null && !courseDTO.getVideoUrl().isEmpty()) {
            currentLesson = new com.skillhub.dto.LectureDTO();
            currentLesson.setTitle("Course Overview");
            currentLesson.setVideoUrl(courseDTO.getVideoUrl());
            currentLesson.setDurationSeconds(0);
            currentLesson.setId(-1L); // Virtual ID
        }

        model.addAttribute("course", courseDTO);
        model.addAttribute("currentLesson", currentLesson);
        // Debug
        // System.out.println("Course Sections: " + courseDTO.getSections().size());

        return "course/learning_room";
    }

    @org.springframework.beans.factory.annotation.Autowired
    private com.skillhub.repository.WishlistRepository wishlistRepository;

    @org.springframework.web.bind.annotation.PostMapping("/course/wishlist/add")
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

        return "redirect:/student/wishlist";
    }

    @org.springframework.web.bind.annotation.PostMapping("/course/wishlist/remove")
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

    @GetMapping("/user/course/{id}/exam")
    public String getExamPage(@PathVariable Long id, Model model, Principal principal) {
        if (principal == null) {
            return "redirect:/login";
        }
        com.skillhub.model.User user = userService.findByEmail(principal.getName());
        com.skillhub.model.Course course = courseService.getCourseById(id);

        // check if enrolled
        if (enrollmentRepository.findByStudentAndCourse(user, course).isEmpty()) {
            return "redirect:/user/course/" + id;
        }

        model.addAttribute("course", course);
        return "course/exam";
    }

    @org.springframework.web.bind.annotation.PostMapping("/user/course/{id}/exam/pass")
    public String passExam(@PathVariable Long id, Principal principal) {
        if (principal == null) {
            return "redirect:/login";
        }
        com.skillhub.model.User user = userService.findByEmail(principal.getName());
        com.skillhub.model.Course course = courseService.getCourseById(id);

        enrollmentRepository.findByStudentAndCourse(user, course).ifPresent(enrollment -> {
            enrollment.setExamPassed(true);
            enrollment.setCertificateDate(java.time.LocalDateTime.now());
            enrollmentRepository.save(enrollment);
        });

        return "redirect:/user/home?certificateData=earned";
    }
}
