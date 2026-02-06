package com.skillhub.controller;

import com.skillhub.model.Course;
import com.skillhub.model.User;
import com.skillhub.service.CategoryService;
import com.skillhub.service.CourseService;
import com.skillhub.service.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.security.Principal;
import java.util.List;

@Controller
@RequestMapping("/instructor")
public class InstructorController {

    @Autowired
    private CourseService courseService;

    @Autowired
    private CategoryService categoryService;

    @Autowired
    private UserService userService;

    @Autowired
    private com.skillhub.service.FileStorageService fileStorageService;

    @GetMapping("/dashboard")
    public String dashboard(Model model, Principal principal) {
        String email = principal.getName();
        List<Course> courses = courseService.getCoursesByInstructor(email);

        int totalStudents = 0;
        double averageRating = 0.0;
        int totalReviews = 0;

        for (Course course : courses) {
            // Note: In production, use dedicated JPQL queries for performance
            if (course.getEnrollments() != null) {
                totalStudents += course.getEnrollments().size();
            }
            if (course.getAverageRating() != null) {
                averageRating += course.getAverageRating() * course.getReviewCount();
                totalReviews += course.getReviewCount();
            }
        }

        if (totalReviews > 0) {
            averageRating /= totalReviews;
        }

        model.addAttribute("courses", courses);
        model.addAttribute("totalStudents", totalStudents);
        model.addAttribute("averageRating", String.format("%.1f", averageRating));
        model.addAttribute("totalReviews", totalReviews);

        return "instructor/dashboard";
    }

    @GetMapping("/courses/create")
    public String createCourseForm(Model model) {
        model.addAttribute("course", new Course());
        model.addAttribute("categories", categoryService.getAllCategories());
        return "instructor/create_course"; // Template to be created
    }

    @PostMapping("/courses/save")
    public String saveCourse(@ModelAttribute Course course, Principal principal,
            RedirectAttributes redirectAttributes) {
        String email = principal.getName();
        User instructor = userService.findByEmail(email);

        course.setInstructor(instructor);
        course.setImageUrl("https://img-c.udemycdn.com/course/480x270/placeholder.jpg"); // Default
        course.setLastUpdated(java.time.LocalDate.now().toString());

        courseService.saveCourse(course);
        redirectAttributes.addFlashAttribute("success", "Course created successfully!");
        return "redirect:/instructor/dashboard";
    }

    @GetMapping("/courses/{id}/edit")
    public String editCourse(@PathVariable Long id, Model model) {
        Course course = courseService.getCourseById(id);
        model.addAttribute("course", course);
        model.addAttribute("categories", categoryService.getAllCategories());
        return "instructor/edit_course";
    }

    @PostMapping("/courses/{id}/sections/add")
    public String addSection(@PathVariable Long id, @RequestParam String title, RedirectAttributes redirectAttributes) {
        courseService.addSectionToCourse(id, title);
        redirectAttributes.addFlashAttribute("success", "Section added successfully!");
        return "redirect:/instructor/courses/" + id + "/edit?tab=curriculum";
    }

    @PostMapping("/sections/{id}/lectures/add")
    public String addLecture(@PathVariable Long id,
            @RequestParam("title") String title,
            @RequestParam("durationSeconds") Integer durationSeconds,
            @RequestParam(value = "videoFile", required = false) org.springframework.web.multipart.MultipartFile videoFile,
            @RequestParam(value = "videoUrl", required = false) String videoUrl,
            @RequestHeader(value = "Referer", required = false) String referer,
            RedirectAttributes redirectAttributes) {

        com.skillhub.model.Lecture lecture = new com.skillhub.model.Lecture();
        lecture.setTitle(title);
        lecture.setDurationSeconds(durationSeconds);

        if (videoFile != null && !videoFile.isEmpty()) {
            String fileUrl = fileStorageService.store(videoFile);
            lecture.setVideoUrl(fileUrl);
        } else if (videoUrl != null && !videoUrl.isEmpty()) {
            lecture.setVideoUrl(videoUrl);
        }

        courseService.addLectureToSection(id, lecture);

        redirectAttributes.addFlashAttribute("success", "Lecture added successfully!");
        return (referer != null) ? "redirect:" + referer : "redirect:/instructor/dashboard";
    }
}
