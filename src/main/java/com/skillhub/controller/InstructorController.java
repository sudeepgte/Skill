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
@RequestMapping("/vendor")
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

    @Autowired
    private com.skillhub.repository.ExamAttemptRepository examAttemptRepository;

    @Autowired
    private org.springframework.security.crypto.password.PasswordEncoder passwordEncoder;

    @GetMapping("/dashboard")
    public String dashboard(@RequestParam(value = "tab", defaultValue = "courses") String tab,
            Model model, Principal principal) {
        String email = principal.getName();
        User instructor = userService.findByEmail(email);
        List<Course> courses = courseService.getCoursesByInstructor(email);

        long totalStudents = enrollmentRepository.countByInstructorEmail(email);
        Long viewsObj = courseService.sumViewsByInstructorEmail(email);
        long totalViews = viewsObj != null ? viewsObj : 0L;
        long totalExamsPassed = enrollmentRepository.countExamsPassedByInstructorEmail(email);

        double totalRating = 0.0;
        int totalReviews = 0;

        for (Course course : courses) {
            Double avg = reviewRepository.getAverageRatingByCourseId(course.getId()).orElse(0.0);
            Integer count = reviewRepository.countByCourseId(course.getId());
            
            course.setAverageRating(Math.round(avg * 10.0) / 10.0);
            course.setReviewCount(count);
            
            totalRating += avg * count;
            totalReviews += count;
        }

        double averageRating = (totalReviews > 0) ? totalRating / totalReviews : 0.0;
        List<com.skillhub.model.CourseQuestion> questionsList = courseQuestionRepository.findByInstructorEmail(email);

        model.addAttribute("instructor", instructor);
        model.addAttribute("vendor", instructor);
        model.addAttribute("user", instructor); // Explicitly add for chat widget alignment
        model.addAttribute("courses", courses);
        model.addAttribute("totalStudents", totalStudents);
        model.addAttribute("totalViews", totalViews);
        model.addAttribute("averageRating", String.format("%.1f", averageRating));
        model.addAttribute("totalReviews", totalReviews);
        model.addAttribute("totalQuestions", questionsList.size());
        model.addAttribute("totalExamsPassed", totalExamsPassed);
        model.addAttribute("activeTab", tab);

        if ("communications".equals(tab)) {
            model.addAttribute("reviews", reviewRepository.findByInstructorEmail(email));
            model.addAttribute("questions", questionsList);
        } else if ("reviews".equals(tab)) {
            model.addAttribute("reviews", reviewRepository.findByInstructorEmail(email));
        } else if ("questions".equals(tab)) {
            model.addAttribute("questions", questionsList);
        } else if ("students".equals(tab)) {
            List<com.skillhub.model.Enrollment> enrollments = enrollmentRepository.findByInstructorEmail(email);
            for (com.skillhub.model.Enrollment enrollment : enrollments) {
                long total = lectureProgressRepository.countTotalLecturesByCourse(enrollment.getCourse().getId());
                double lectureBasedPct = 0.0;
                if (total > 0) {
                    long completed = lectureProgressRepository.countCompletedLecturesByCourse(
                            enrollment.getStudent().getId(), enrollment.getCourse().getId());
                    lectureBasedPct = Math.min(100.0, (double) completed / total * 100.0);
                }
                // Use whichever is higher: discrete lecture completion OR partial video watch %
                double storedPct = enrollment.getProgressPercentage();
                enrollment.setProgressPercentage(Math.max(lectureBasedPct, storedPct));
            }
            model.addAttribute("enrollments", enrollments);
            
            List<com.skillhub.model.ExamAttempt> examAttempts = examAttemptRepository.findByInstructorEmail(email);
            examAttempts.sort((a, b) -> b.getAttemptDate().compareTo(a.getAttemptDate()));
            
            List<Map<String, Object>> examSubmissions = new java.util.ArrayList<>();
            for (com.skillhub.model.ExamAttempt attempt : examAttempts) {
                Map<String, Object> submission = new java.util.HashMap<>();
                submission.put("attempt", attempt);
                com.skillhub.model.Enrollment enrollment = enrollmentRepository.findByStudentAndCourse(attempt.getUser(), attempt.getCourse()).orElse(null);
                submission.put("enrollment", enrollment);
                examSubmissions.add(submission);
            }
            model.addAttribute("examSubmissions", examSubmissions);
        } else if ("analytics".equals(tab)) {
            List<com.skillhub.model.Enrollment> allEnrollments = enrollmentRepository.findByInstructorEmail(email);
            List<com.skillhub.model.Enrollment> passedExams = new java.util.ArrayList<>();
            for (com.skillhub.model.Enrollment e : allEnrollments) {
                if (e.isExamPassed()) {
                    passedExams.add(e);
                }
            }
            model.addAttribute("passedExams", passedExams);
        }

        return "instructor/dashboard";
    }

    /** REST endpoint â€“ returns live progress for all enrolled students (JSON) */
    @GetMapping("/api/students-progress")
    @ResponseBody
    public ResponseEntity<List<Map<String, Object>>> getStudentsProgress(Principal principal) {
        if (principal == null)
            return ResponseEntity.status(401).build();
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

    @GetMapping("/api/dashboard-overview")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> getDashboardOverview(Principal principal) {
        if (principal == null)
            return ResponseEntity.status(401).build();
        String email = principal.getName();
        List<com.skillhub.model.Course> courses = courseService.getCoursesByInstructor(email);

        long totalEnrolled = enrollmentRepository.countByInstructorEmail(email);
        Long viewsObj = courseService.sumViewsByInstructorEmail(email);
        long totalViews = viewsObj != null ? viewsObj : 0L;
        long totalExamsPassed = enrollmentRepository.countExamsPassedByInstructorEmail(email);

        double totalRating = 0.0;
        int totalReviews = 0;

        for (com.skillhub.model.Course course : courses) {
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
        stats.put("totalExamsPassed", totalExamsPassed);

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
            @RequestParam(value = "promoVideo", required = false) MultipartFile promoVideo,
            Principal principal,
            RedirectAttributes redirectAttributes) {
        String email = principal.getName();
        User instructor = userService.findByEmail(email);

        course.setInstructor(instructor);

        if (course.getPrice() != null && course.getPrice() < 0) {
            redirectAttributes.addFlashAttribute("error", "Course price cannot be negative.");
            return "redirect:/vendor/courses/create";
        }

        if (thumbnail != null && !thumbnail.isEmpty()) {
            String mimeType = thumbnail.getContentType();
            if (mimeType == null || !mimeType.startsWith("image/")) {
                redirectAttributes.addFlashAttribute("error", "Thumbnail must be an image file.");
                return "redirect:/vendor/courses/create";
            }
            course.setImageUrl(fileStorageService.store(thumbnail));
        } else if (course.getId() == null) {
            course.setImageUrl("https://img-c.udemycdn.com/course/480x270/placeholder.jpg"); // Default for new
        } else {
            Course existingCourse = courseService.getCourseById(course.getId());
            if (course.getImageUrl() == null || course.getImageUrl().isEmpty()) {
                course.setImageUrl(existingCourse.getImageUrl());
            }
        }
        
        // Process Promotional Video
        if (promoVideo != null && !promoVideo.isEmpty()) {
            String mimeType = promoVideo.getContentType();
            if (mimeType == null || !mimeType.startsWith("video/")) {
                redirectAttributes.addFlashAttribute("error", "Promotional video must be a valid video file.");
                return "redirect:/vendor/courses/create";
            }
            course.setVideoUrl(fileStorageService.store(promoVideo));
        }

        if (course.getId() == null) {
            course.setPublished(false);
            course.setStatus("DRAFT");
        }

        course.setLastUpdated(java.time.LocalDate.now().toString());

        // Backend Validation: Must start with a letter
        java.util.function.Predicate<String> startsWithLetter = s -> s != null && !s.trim().isEmpty() && Character.isLetter(s.trim().charAt(0));

        if (!startsWithLetter.test(course.getTitle())) {
            redirectAttributes.addFlashAttribute("error", "Course Title must start with a letter.");
            return "redirect:/vendor/courses/create";
        }
        if (course.getSubtitle() != null && !course.getSubtitle().trim().isEmpty() && !startsWithLetter.test(course.getSubtitle())) {
            redirectAttributes.addFlashAttribute("error", "Course Subtitle must start with a letter.");
            return "redirect:/vendor/courses/create";
        }
        if (!startsWithLetter.test(course.getDescription())) {
            redirectAttributes.addFlashAttribute("error", "Course Description must start with a letter.");
            return "redirect:/vendor/courses/create";
        }
        if (course.getLearningObjectives() != null && !course.getLearningObjectives().trim().isEmpty() && !startsWithLetter.test(course.getLearningObjectives())) {
            redirectAttributes.addFlashAttribute("error", "Learning Objectives must start with a letter.");
            return "redirect:/vendor/courses/create";
        }


        // Process Curriculum Videos and Validate Titles
        if (course.getSections() != null) {
            for (com.skillhub.model.CourseSection section : course.getSections()) {
                if (!startsWithLetter.test(section.getTitle())) {
                    redirectAttributes.addFlashAttribute("error", "Section titles must start with a letter.");
                    return "redirect:/vendor/courses/create";
                }
                section.setCourse(course);
                if (section.getLectures() != null) {
                    for (com.skillhub.model.Lecture lecture : section.getLectures()) {
                        if (!startsWithLetter.test(lecture.getTitle())) {
                            redirectAttributes.addFlashAttribute("error", "Lecture titles must start with a letter.");
                            return "redirect:/vendor/courses/create";
                        }
                        if (lecture.getVideoFile() != null && !lecture.getVideoFile().isEmpty()) {
                            String lecMimeType = lecture.getVideoFile().getContentType();
                            if (lecMimeType == null || !lecMimeType.startsWith("video/")) {
                                redirectAttributes.addFlashAttribute("error", "Lecture video must be a valid video file.");
                                return "redirect:/vendor/courses/create";
                            }
                            lecture.setVideoUrl(fileStorageService.store(lecture.getVideoFile()));
                        } else if (course.getId() != null && lecture.getId() != null) {
                            // Fetch existing videoUrl if possible (will handle in service if needed)
                        }
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
        return "redirect:/vendor/dashboard";
    }

    @GetMapping("/courses/{id}/edit")
    public String editCourse(@PathVariable("id") Long id, Model model) {
        Course course = courseService.getCourseById(id);
        model.addAttribute("course", course);
        model.addAttribute("categories", categoryService.getAllCategories());
        return "instructor/edit_course";
    }

    @PostMapping("/courses/{id}/status")
    @org.springframework.web.bind.annotation.ResponseBody
    public org.springframework.http.ResponseEntity<?> updateCourseStatus(@PathVariable("id") Long id,
            @RequestParam("published") boolean published) {
        try {
            if (published) {
                courseService.publishCourse(id);
            }
            // Logic to unpublish is removed as courses are now always published
            return org.springframework.http.ResponseEntity.ok().build();
        } catch (Exception e) {
            return org.springframework.http.ResponseEntity.badRequest().build();
        }
    }

    @PostMapping("/courses/{id}/submit")
    public String submitCourse(@PathVariable("id") Long id, Principal principal, RedirectAttributes redirectAttributes) {
        String email = principal.getName();
        Course course = courseService.getCourseById(id);
        if (course != null && course.getInstructor().getEmail().equals(email)) {
            course.setStatus("PENDING_REVIEW");
            courseService.saveCourse(course);
            redirectAttributes.addFlashAttribute("success", "Course submitted for review successfully!");
        } else {
            redirectAttributes.addFlashAttribute("error", "Unable to submit course.");
        }
        return "redirect:/vendor/dashboard?tab=courses";
    }

    @PostMapping("/courses/{id}/delete")
    public String deleteCourse(@PathVariable("id") Long id, Principal principal,
            RedirectAttributes redirectAttributes) {
        String email = principal.getName();
        Course course = courseService.getCourseById(id);

        if (course != null && course.getInstructor().getEmail().equals(email)) {
            courseService.deleteCourse(id);
            redirectAttributes.addFlashAttribute("success", "Course deleted successfully!");
        } else {
            redirectAttributes.addFlashAttribute("error", "Unable to delete course.");
        }
        return "redirect:/vendor/dashboard?tab=courses";
    }

    @PostMapping("/courses/{id}/sections/add")
    public String addSection(@PathVariable("id") Long id, @RequestParam("title") String title,
            RedirectAttributes redirectAttributes) {
        courseService.addSectionToCourse(id, title);
        redirectAttributes.addFlashAttribute("success", "Section added successfully!");
        return "redirect:/vendor/courses/" + id + "/edit?tab=curriculum";
    }

    @PostMapping("/sections/{id}/lectures/add")
    public String addLecture(@PathVariable("id") Long id,
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
        }

        courseService.addLectureToSection(id, lecture);

        redirectAttributes.addFlashAttribute("success", "Lecture added successfully!");
        return (referer != null) ? "redirect:" + referer : "redirect:/vendor/dashboard";
    }

    @PostMapping("/profile/update")
    public String updateProfile(
            @RequestParam("name") String name,
            @RequestParam(value = "bio", required = false) String bio,
            @RequestParam(value = "skills", required = false) String skills,
            @RequestParam(value = "expertise", required = false) String expertise,
            @RequestParam(value = "instagramUrl", required = false) String instagramUrl,
            @RequestParam(value = "linkedinUrl", required = false) String linkedinUrl,
            @RequestParam(value = "youtubeUrl", required = false) String youtubeUrl,
            @RequestParam(value = "profilePicture", required = false) MultipartFile profilePicture,
            @RequestParam(value = "currentPassword", required = false) String currentPassword,
            @RequestParam(value = "newPassword", required = false) String newPassword,
            @RequestParam(value = "confirmPassword", required = false) String confirmPassword,
            Principal principal,
            RedirectAttributes redirectAttributes) {
        User user = userService.findByEmail(principal.getName());
        user.setName(name);
        user.setBio(bio);
        user.setSkills(skills);
        user.setExpertise(expertise);
        user.setInstagramUrl(instagramUrl);
        user.setLinkedinUrl(linkedinUrl);
        user.setYoutubeUrl(youtubeUrl);

        // Process Profile Photo
        if (profilePicture != null && !profilePicture.isEmpty()) {
            try {
                String imagePath = fileStorageService.store(profilePicture);
                user.setProfileImageUrl(imagePath);
            } catch (Exception e) {
                redirectAttributes.addFlashAttribute("error", "Failed to upload image: " + e.getMessage());
                return "redirect:/vendor/dashboard?tab=profile";
            }
        }

        // Process Password Change
        if (newPassword != null && !newPassword.isEmpty()) {
            if (currentPassword == null || currentPassword.isEmpty()) {
                redirectAttributes.addFlashAttribute("error", "Current password is required to set a new password.");
                return "redirect:/vendor/dashboard?tab=profile";
            }
            if (!passwordEncoder.matches(currentPassword, user.getPassword())) {
                redirectAttributes.addFlashAttribute("error", "Incorrect current password.");
                return "redirect:/vendor/dashboard?tab=profile";
            }
            if (!newPassword.equals(confirmPassword)) {
                redirectAttributes.addFlashAttribute("error", "New passwords do not match.");
                return "redirect:/vendor/dashboard?tab=profile";
            }
            user.setPassword(passwordEncoder.encode(newPassword));
        }

        userService.saveUser(user);
        redirectAttributes.addFlashAttribute("success", "Profile updated successfully!");
        return "redirect:/vendor/dashboard?tab=profile";
    }

    @PostMapping("/reviews/{id}/reply")
    public String replyToReview(@PathVariable("id") Long id, @RequestParam("reply") String reply,
            RedirectAttributes redirectAttributes) {
        com.skillhub.model.Review review = reviewRepository.findById(id).orElse(null);
        if (review != null) {
            review.setReply(reply);
            review.setReplyDate(java.time.LocalDateTime.now());
            reviewRepository.save(review);
            redirectAttributes.addFlashAttribute("success", "Reply sent!");
        }
        return "redirect:/vendor/dashboard?tab=communications";
    }

    @PostMapping("/questions/{id}/reply")
    public String replyToQuestion(@PathVariable("id") Long id, @RequestParam("content") String content,
            Principal principal, RedirectAttributes redirectAttributes) {
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
        return "redirect:/vendor/dashboard?tab=communications";
    }

    @PostMapping("/enrollments/{id}/certificate")
    public String generateCertificate(@PathVariable("id") Long id, RedirectAttributes redirectAttributes) {
        com.skillhub.model.Enrollment enrollment = enrollmentRepository.findById(id).orElse(null);
        if (enrollment != null) {
            enrollment.setExamPassed(true);
            enrollment.setCertificateDate(java.time.LocalDateTime.now());
            enrollmentRepository.save(enrollment);
            redirectAttributes.addFlashAttribute("success",
                    "Certificate generated for student: " + enrollment.getStudent().getName());
        }
        return "redirect:/vendor/dashboard?tab=students";
    }

    @GetMapping("/exams")
    public String listExams(Model model, Principal principal) {
        String email = principal.getName();
        User instructor = userService.findByEmail(email);
        model.addAttribute("courses", courseService.getCoursesByInstructor(email));
        model.addAttribute("activeTab", "exams");
        model.addAttribute("instructor", instructor);
        model.addAttribute("vendor", instructor);
        return "instructor/exams";
    }

    /** REST endpoint â€“ returns question counts for each course (JSON) */
    @GetMapping("/api/exams-stats")
    @ResponseBody
    public ResponseEntity<Map<Long, Integer>> getExamsStats(Principal principal) {
        if (principal == null)
            return ResponseEntity.status(401).build();
        String email = principal.getName();
        List<com.skillhub.model.Course> courses = courseService.getCoursesByInstructor(email);
        Map<Long, Integer> stats = new HashMap<>();
        for (com.skillhub.model.Course course : courses) {
            stats.put(course.getId(), course.getExamQuestions() != null ? course.getExamQuestions().size() : 0);
        }
        return ResponseEntity.ok(stats);
    }

    @GetMapping("/courses/{id}/questions")
    public String manageExamQuestions(@PathVariable("id") Long id, Model model, Principal principal) {
        com.skillhub.model.Course course = courseService.getCourseById(id);
        String email = principal.getName();

        if (course == null || !course.getInstructor().getEmail().equals(email)) {
            return "redirect:/vendor/exams";
        }

        model.addAttribute("course", course);
        model.addAttribute("questions", course.getExamQuestions());
        User instructor = userService.findByEmail(email);
        model.addAttribute("instructor", instructor);
        model.addAttribute("vendor", instructor);
        return "instructor/manage_questions";
    }

    @PostMapping("/courses/{id}/questions/add")
    public String addExamQuestion(@PathVariable("id") Long id,
            @RequestParam("questionText") String questionText,
            @RequestParam("optionA") String optionA,
            @RequestParam("optionB") String optionB,
            @RequestParam("optionC") String optionC,
            @RequestParam("optionD") String optionD,
            @RequestParam("correctOption") String correctOption,
            Principal principal) {
        com.skillhub.model.Course course = courseService.getCourseById(id);
        String email = principal.getName();

        if (course != null && course.getInstructor().getEmail().equals(email)) {
            com.skillhub.model.ExamQuestion q = new com.skillhub.model.ExamQuestion(course, questionText, optionA,
                    optionB, optionC, optionD, correctOption);
            course.getExamQuestions().add(q);
            courseService.saveCourse(course);
        }
        return "redirect:/vendor/courses/" + id + "/questions";
    }

    @PostMapping("/courses/{id}/questions/delete/{qid}")
    public String deleteExamQuestion(@PathVariable("id") Long id,
            @PathVariable("qid") Long qid,
            Principal principal) {
        com.skillhub.model.Course course = courseService.getCourseById(id);
        String email = principal.getName();

        if (course != null && course.getInstructor().getEmail().equals(email)) {
            course.getExamQuestions().removeIf(q -> q.getId().equals(qid));
            courseService.saveCourse(course);
        }
        return "redirect:/vendor/courses/" + id + "/questions";
    }
}
