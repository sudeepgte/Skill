package com.skillhub.controller;

import com.skillhub.model.Course;
import com.skillhub.model.User;
import com.skillhub.repository.EnrollmentRepository;
import com.skillhub.repository.ReviewRepository;
import com.skillhub.service.CategoryService;
import com.skillhub.service.CourseService;
import com.skillhub.service.FileStorageService;
import com.skillhub.service.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.security.Principal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ResponseBody;

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
    private EnrollmentRepository enrollmentRepository;
 
    @Autowired
    private ReviewRepository reviewRepository;
 
    @Autowired
    private FileStorageService fileStorageService;

    @Autowired
    private com.skillhub.repository.LectureProgressRepository lectureProgressRepository;
    
    @Autowired
    private com.skillhub.repository.CourseQuestionRepository courseQuestionRepository;
    
    @Autowired
    private com.skillhub.repository.CourseAnswerRepository courseAnswerRepository;

    @GetMapping("/dashboard")
    public String dashboard(@RequestParam(value = "tab", defaultValue = "courses") String tab,
                           Model model, Principal principal) {
        String email = principal.getName();
        User instructor = userService.findByEmail(email);
        List<Course> courses = courseService.getCoursesByInstructor(email);

        int totalStudents = 0;
        double totalRating = 0.0;
        int totalReviews = 0;
        long totalViews = 0;

        for (Course course : courses) {
            if (course.getEnrollments() != null) {
                totalStudents += course.getEnrollments().size();
            }
            if (course.getViewCount() != null) {
                totalViews += course.getViewCount();
            }
            // Populate ratings
            Double avg = reviewRepository.getAverageRatingByCourseId(course.getId()).orElse(0.0);
            Integer count = reviewRepository.countByCourseId(course.getId());
            totalRating += avg * count;
            totalReviews += count;
        }

        double averageRating = (totalReviews > 0) ? totalRating / totalReviews : 0.0;
        List<com.skillhub.model.CourseQuestion> questionsList = courseQuestionRepository.findByInstructorEmail(email);

        model.addAttribute("instructor", instructor);
        model.addAttribute("courses", courses);
        model.addAttribute("totalStudents", totalStudents);
        model.addAttribute("totalViews", totalViews);
        model.addAttribute("averageRating", String.format("%.1f", averageRating));
        model.addAttribute("totalReviews", totalReviews);
        model.addAttribute("totalQuestions", questionsList.size());
        model.addAttribute("activeTab", tab);
        
        if ("communications".equals(tab)) {
            model.addAttribute("reviews", reviewRepository.findByInstructorEmail(email));
            model.addAttribute("questions", questionsList);
        } else if ("students".equals(tab)) {
            List<com.skillhub.model.Enrollment> enrollments = enrollmentRepository.findByInstructorEmail(email);
            for (com.skillhub.model.Enrollment enrollment : enrollments) {
                long total = lectureProgressRepository.countTotalLecturesByCourse(enrollment.getCourse().getId());
                double lectureBasedPct = 0.0;
                if (total > 0) {
                    long completed = lectureProgressRepository.countCompletedLecturesByCourse(enrollment.getStudent().getId(), enrollment.getCourse().getId());
                    lectureBasedPct = Math.min(100.0, (double) completed / total * 100.0);
                }
                // Use whichever is higher: discrete lecture completion OR partial video watch %
                double storedPct = enrollment.getProgressPercentage();
                enrollment.setProgressPercentage(Math.max(lectureBasedPct, storedPct));
            }
            model.addAttribute("enrollments", enrollments);
        }

        return "instructor/dashboard";
    }

    /** REST endpoint – returns live progress for all enrolled students (JSON) */
    @GetMapping("/api/students-progress")
    @ResponseBody
    public ResponseEntity<List<Map<String, Object>>> getStudentsProgress(Principal principal) {
        if (principal == null) return ResponseEntity.status(401).build();
        String email = principal.getName();
        List<com.skillhub.model.Enrollment> enrollments = enrollmentRepository.findByInstructorEmail(email);
        List<Map<String, Object>> result = new ArrayList<>();
        for (com.skillhub.model.Enrollment enrollment : enrollments) {
            long total = lectureProgressRepository.countTotalLecturesByCourse(enrollment.getCourse().getId());
            double lectureBasedPct = 0.0;
            if (total > 0) {
                long completed = lectureProgressRepository.countCompletedLecturesByCourse(
                        enrollment.getStudent().getId(), enrollment.getCourse().getId());
                lectureBasedPct = Math.min(100.0, (double) completed / total * 100.0);
            }
            double pct = Math.max(lectureBasedPct, enrollment.getProgressPercentage());
            Map<String, Object> row = new HashMap<>();
            row.put("enrollmentId", enrollment.getId());
            row.put("pct", Math.round(pct));
            result.add(row);
        }
        return ResponseEntity.ok(result);
    }

    /** REST endpoint – returns overall stats (JSON) */
    @GetMapping("/api/dashboard-overview")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> getDashboardOverview(Principal principal) {
        if (principal == null) return ResponseEntity.status(401).build();
        String email = principal.getName();
        List<com.skillhub.model.Course> courses = courseService.getCoursesByInstructor(email);

        int totalEnrolled = 0;
        long totalViews = 0;
        double totalRating = 0.0;
        int totalReviews = 0;

        for (com.skillhub.model.Course course : courses) {
            totalEnrolled += (course.getEnrollments() != null ? course.getEnrollments().size() : 0);
            totalViews += (course.getViewCount() != null ? course.getViewCount() : 0);
            Double avg = reviewRepository.getAverageRatingByCourseId(course.getId()).orElse(0.0);
            Integer count = reviewRepository.countByCourseId(course.getId());
            totalRating += avg * count;
            totalReviews += count;
        }

        int totalQuestions = courseQuestionRepository.findByInstructorEmail(email).size();
        double avgRating = (totalReviews > 0) ? totalRating / totalReviews : 0.0;

        Map<String, Object> stats = new HashMap<>();
        stats.put("totalEnrolled", totalEnrolled);
        stats.put("totalViews", totalViews);
        stats.put("averageRating", String.format("%.1f", avgRating));
        stats.put("totalReviews", totalReviews);
        stats.put("totalQuestions", totalQuestions);

        return ResponseEntity.ok(stats);
    }

    @GetMapping("/courses/create")
    public String createCourseForm(Model model) {
        Course course = new Course();
        course.setCategory(new com.skillhub.model.Category());
        model.addAttribute("course", course);
        model.addAttribute("categories", categoryService.getAllCategories());
        return "instructor/create_course";
    }

    @PostMapping("/courses/save")
    public String saveCourse(@ModelAttribute Course course,
            @RequestParam(value = "thumbnail", required = false) MultipartFile thumbnail,
            Principal principal,
            RedirectAttributes redirectAttributes) {
        String email = principal.getName();
        User instructor = userService.findByEmail(email);

        course.setInstructor(instructor);
        
        if (thumbnail != null && !thumbnail.isEmpty()) {
            course.setImageUrl(fileStorageService.store(thumbnail));
        } else if (course.getId() == null) {
            course.setImageUrl("https://img-c.udemycdn.com/course/480x270/placeholder.jpg"); // Default for new
        } else {
            // For edits, if no new thumbnail is uploaded, we should keep the existing imageUrl
            // Fetch the current course from DB to get the existing image URL if it was not in the form
            Course existingCourse = courseService.getCourseById(course.getId());
            if (course.getImageUrl() == null || course.getImageUrl().isEmpty()) {
                course.setImageUrl(existingCourse.getImageUrl());
            }
        }
        
        if (course.getId() == null) {
            course.setPublished(true);
        }
        
        course.setLastUpdated(java.time.LocalDate.now().toString());

        // Process Curriculum Videos
        if (course.getSections() != null) {
            for (com.skillhub.model.CourseSection section : course.getSections()) {
                section.setCourse(course);
                if (section.getLectures() != null) {
                    for (com.skillhub.model.Lecture lecture : section.getLectures()) {
                        lecture.setSection(section);
                        // If file uploaded, store it
                        if (lecture.getVideoFile() != null && !lecture.getVideoFile().isEmpty()) {
                            String storedUrl = fileStorageService.store(lecture.getVideoFile());
                            lecture.setVideoUrl(storedUrl);
                        }
                    }
                }
            }
        }

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

    @PostMapping("/courses/{id}/status")
    @org.springframework.web.bind.annotation.ResponseBody
    public org.springframework.http.ResponseEntity<?> updateCourseStatus(@PathVariable Long id, @RequestParam boolean published) {
        try {
            if (published) {
                courseService.publishCourse(id);
            } else {
                courseService.unpublishCourse(id);
            }
            return org.springframework.http.ResponseEntity.ok().build();
        } catch (Exception e) {
            return org.springframework.http.ResponseEntity.badRequest().build();
        }
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

    @PostMapping("/profile/update")
    public String updateProfile(@ModelAttribute User userUpdate, Principal principal, RedirectAttributes redirectAttributes) {
        User user = userService.findByEmail(principal.getName());
        user.setName(userUpdate.getName());
        user.setBio(userUpdate.getBio());
        user.setSkills(userUpdate.getSkills());
        user.setExpertise(userUpdate.getExpertise());
        user.setInstagramUrl(userUpdate.getInstagramUrl());
        user.setLinkedinUrl(userUpdate.getLinkedinUrl());
        user.setYoutubeUrl(userUpdate.getYoutubeUrl());
        
        userService.saveUser(user);
        redirectAttributes.addFlashAttribute("success", "Profile updated successfully!");
        return "redirect:/instructor/dashboard?tab=profile";
    }

    @PostMapping("/reviews/{id}/reply")
    public String replyToReview(@PathVariable Long id, @RequestParam String reply, RedirectAttributes redirectAttributes) {
        com.skillhub.model.Review review = reviewRepository.findById(id).orElse(null);
        if (review != null) {
            review.setReply(reply);
            review.setReplyDate(java.time.LocalDateTime.now());
            reviewRepository.save(review);
            redirectAttributes.addFlashAttribute("success", "Reply sent!");
        }
        return "redirect:/instructor/dashboard?tab=communications";
    }

    @PostMapping("/questions/{id}/reply")
    public String replyToQuestion(@PathVariable Long id, @RequestParam String content, Principal principal, RedirectAttributes redirectAttributes) {
        com.skillhub.model.CourseQuestion question = courseQuestionRepository.findById(id).orElse(null);
        if (question != null) {
            com.skillhub.model.User instructor = userService.findByEmail(principal.getName());
            com.skillhub.model.CourseAnswer answer = new com.skillhub.model.CourseAnswer();
            answer.setQuestion(question);
            answer.setUser(instructor);
            answer.setContent(content);
            courseAnswerRepository.save(answer);
            redirectAttributes.addFlashAttribute("success", "Answer posted successfully!");
        }
        return "redirect:/instructor/dashboard?tab=communications";
    }

    @PostMapping("/enrollments/{id}/certificate")
    public String generateCertificate(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        com.skillhub.model.Enrollment enrollment = enrollmentRepository.findById(id).orElse(null);
        if (enrollment != null) {
            enrollment.setExamPassed(true);
            enrollment.setCertificateDate(java.time.LocalDateTime.now());
            enrollmentRepository.save(enrollment);
            redirectAttributes.addFlashAttribute("success", "Certificate generated for student: " + enrollment.getStudent().getName());
        }
        return "redirect:/instructor/dashboard?tab=students";
    }
}
