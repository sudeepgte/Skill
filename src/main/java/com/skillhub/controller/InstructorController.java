package com.skillhub.controller;

import com.skillhub.model.*;
import com.skillhub.repository.*;
import com.skillhub.service.CategoryService;
import com.skillhub.service.CourseService;
import com.skillhub.service.FileStorageService;
import com.skillhub.service.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.security.Principal;
import java.time.LocalDateTime;
import java.util.*;
import java.util.function.Predicate;

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
    private LectureProgressRepository lectureProgressRepository;

    @Autowired
    private CourseQuestionRepository courseQuestionRepository;

    @Autowired
    private CourseAnswerRepository courseAnswerRepository;

    @Autowired
    private ExamAttemptRepository examAttemptRepository;

    @Autowired
    private CourseResourceRepository courseResourceRepository;

    @Autowired
    private CourseSectionRepository courseSectionRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    // Helper method to enforce ownership validation
    private Course getOwnedCourseOrThrow(Long courseId, String email) {
        Course course = courseService.getCourseById(courseId);
        if (course == null) {
            throw new NoSuchElementException("Course not found with id: " + courseId);
        }
        if (course.getInstructor() == null || !course.getInstructor().getEmail().equals(email)) {
            throw new AccessDeniedException("Unauthorized: You do not own this course");
        }
        return course;
    }

    @GetMapping("/dashboard")
    public String dashboard(@RequestParam(value = "tab", defaultValue = "courses") String tab,
            @RequestParam(value = "status", required = false) String statusFilter,
            Model model, Principal principal) {
        String email = principal.getName();
        User instructor = userService.findByEmail(email);
        List<Course> allInstructorCourses = courseService.getCoursesByInstructor(email);

        List<Course> displayedCourses = allInstructorCourses;
        if (statusFilter != null && !statusFilter.trim().isEmpty() && !"ALL".equalsIgnoreCase(statusFilter)) {
            try {
                CourseStatus cs = CourseStatus.valueOf(statusFilter.toUpperCase());
                displayedCourses = new ArrayList<>();
                for (Course c : allInstructorCourses) {
                    if (c.getStatus() == cs) {
                        displayedCourses.add(c);
                    }
                }
            } catch (Exception ignored) {
            }
        }

        int totalStudents = 0;
        double totalRating = 0.0;
        int totalReviews = 0;
        long totalViews = 0;
        int totalExamsPassed = 0;

        for (Course course : allInstructorCourses) {
            if (course.getEnrollments() != null) {
                totalStudents += course.getEnrollments().size();
            }
            if (course.getViewCount() != null) {
                totalViews += course.getViewCount();
            }
            // Populate ratings
            Double avg = reviewRepository.getAverageRatingByCourseId(course.getId()).orElse(0.0);
            Integer count = reviewRepository.countByCourseId(course.getId());

            course.setAverageRating(Math.round(avg * 10.0) / 10.0);
            course.setReviewCount(count);

            totalRating += avg * count;
            totalReviews += count;

            if (course.getEnrollments() != null) {
                for (Enrollment e : course.getEnrollments()) {
                    if (e.isExamPassed()) {
                        totalExamsPassed++;
                    }
                }
            }
        }

        double averageRating = (totalReviews > 0) ? totalRating / totalReviews : 0.0;
        List<CourseQuestion> questionsList = courseQuestionRepository.findByInstructorEmail(email);

        model.addAttribute("instructor", instructor);
        model.addAttribute("vendor", instructor);
        model.addAttribute("user", instructor);
        model.addAttribute("courses", displayedCourses);
        model.addAttribute("allCourses", allInstructorCourses);
        model.addAttribute("totalStudents", totalStudents);
        model.addAttribute("totalViews", totalViews);
        model.addAttribute("averageRating", String.format("%.1f", averageRating));
        model.addAttribute("totalReviews", totalReviews);
        model.addAttribute("totalQuestions", questionsList.size());
        model.addAttribute("totalExamsPassed", totalExamsPassed);
        model.addAttribute("activeTab", tab);
        model.addAttribute("statusFilter", statusFilter != null ? statusFilter : "ALL");

        if ("communications".equals(tab)) {
            model.addAttribute("reviews", reviewRepository.findByInstructorEmail(email));
            model.addAttribute("questions", questionsList);
        } else if ("reviews".equals(tab)) {
            model.addAttribute("reviews", reviewRepository.findByInstructorEmail(email));
        } else if ("questions".equals(tab)) {
            model.addAttribute("questions", questionsList);
        } else if ("students".equals(tab)) {
            List<Enrollment> enrollments = enrollmentRepository.findByInstructorEmail(email);
            for (Enrollment enrollment : enrollments) {
                long total = lectureProgressRepository.countTotalLecturesByCourse(enrollment.getCourse().getId());
                double lectureBasedPct = 0.0;
                if (total > 0) {
                    long completed = lectureProgressRepository.countCompletedLecturesByCourse(
                            enrollment.getStudent().getId(), enrollment.getCourse().getId());
                    lectureBasedPct = Math.min(100.0, (double) completed / total * 100.0);
                }
                double storedPct = enrollment.getProgressPercentage();
                enrollment.setProgressPercentage(Math.max(lectureBasedPct, storedPct));
            }
            model.addAttribute("enrollments", enrollments);

            List<ExamAttempt> examAttempts = examAttemptRepository.findByInstructorEmail(email);
            examAttempts.sort((a, b) -> b.getAttemptDate().compareTo(a.getAttemptDate()));

            List<Map<String, Object>> examSubmissions = new ArrayList<>();
            for (ExamAttempt attempt : examAttempts) {
                Map<String, Object> submission = new HashMap<>();
                submission.put("attempt", attempt);
                Enrollment enrollment = enrollmentRepository.findByStudentAndCourse(attempt.getUser(), attempt.getCourse()).orElse(null);
                submission.put("enrollment", enrollment);
                examSubmissions.add(submission);
            }
            model.addAttribute("examSubmissions", examSubmissions);
        } else if ("analytics".equals(tab)) {
            List<Enrollment> allEnrollments = enrollmentRepository.findByInstructorEmail(email);
            List<Enrollment> passedExams = new ArrayList<>();
            for (Enrollment e : allEnrollments) {
                if (e.isExamPassed()) {
                    passedExams.add(e);
                }
            }
            model.addAttribute("passedExams", passedExams);
        }

        return "instructor/dashboard";
    }

    /** REST endpoint – returns live progress for all enrolled students (JSON) */
    @GetMapping("/api/students-progress")
    @ResponseBody
    public ResponseEntity<List<Map<String, Object>>> getStudentsProgress(Principal principal) {
        if (principal == null)
            return ResponseEntity.status(401).build();
        String email = principal.getName();
        List<Enrollment> enrollments = enrollmentRepository.findByInstructorEmail(email);
        List<Map<String, Object>> result = new ArrayList<>();
        for (Enrollment enrollment : enrollments) {
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
        List<Course> courses = courseService.getCoursesByInstructor(email);

        int totalEnrolled = 0;
        long totalViews = 0;
        double totalRating = 0.0;
        int totalReviews = 0;
        int totalExamsPassed = 0;

        for (Course course : courses) {
            totalEnrolled += (course.getEnrollments() != null ? course.getEnrollments().size() : 0);
            totalViews += (course.getViewCount() != null ? course.getViewCount() : 0);
            Double avg = reviewRepository.getAverageRatingByCourseId(course.getId()).orElse(0.0);
            Integer count = reviewRepository.countByCourseId(course.getId());
            totalRating += avg * count;
            totalReviews += count;

            if (course.getEnrollments() != null) {
                for (Enrollment e : course.getEnrollments()) {
                    if (e.isExamPassed()) {
                        totalExamsPassed++;
                    }
                }
            }
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

    // Dynamic categories subcategories & fields API
    @GetMapping("/api/categories/{name}/subcategories")
    @ResponseBody
    public ResponseEntity<List<String>> getCategorySubcategories(@PathVariable("name") String name) {
        return ResponseEntity.ok(categoryService.getSubcategoriesForCategory(name));
    }

    @GetMapping("/api/categories/{name}/fields")
    @ResponseBody
    public ResponseEntity<List<Map<String, Object>>> getCategoryFields(@PathVariable("name") String name) {
        return ResponseEntity.ok(categoryService.getCategoryFieldDefinitions(name));
    }

    @GetMapping("/courses/create")
    public String createCourseForm(Model model, Principal principal) {
        Course course = new Course();
        course.setCategory(new Category());
        course.setPrice(0.0);
        course.setStatus(CourseStatus.DRAFT);
        course.setFormat("Video Course");
        course.setTeachingMethod("Self-paced");
        course.setLevel("Beginner");
        course.setLanguage("English");
        course.setAccessDuration("Lifetime Access");

        model.addAttribute("course", course);
        model.addAttribute("categories", categoryService.getAllCategories());
        model.addAttribute("subcategoriesMap", categoryService.getCategorySubcategoriesMap());
        model.addAttribute("categoryFieldsMap", categoryService.getAllCategoryFieldDefinitions());
        model.addAttribute("categoryDataMap", new HashMap<String, String>());
        model.addAttribute("isEdit", false);
        return "instructor/create_course";
    }

    @GetMapping("/courses/{id}/edit")
    public String editCourse(@PathVariable("id") Long id, Model model, Principal principal) {
        String email = principal.getName();
        Course course = getOwnedCourseOrThrow(id, email);

        model.addAttribute("course", course);
        model.addAttribute("categories", categoryService.getAllCategories());
        model.addAttribute("subcategoriesMap", categoryService.getCategorySubcategoriesMap());
        model.addAttribute("categoryFieldsMap", categoryService.getAllCategoryFieldDefinitions());
        model.addAttribute("categoryDataMap", course.getCategoryDataMap());
        model.addAttribute("isEdit", true);
        return "instructor/create_course"; // Unified dynamic wizard supporting both create & edit
    }

    @PostMapping("/courses/save")
    public String saveCourse(@ModelAttribute Course course,
            @RequestParam(value = "action", defaultValue = "draft") String action,
            @RequestParam(value = "thumbnail", required = false) MultipartFile thumbnail,
            @RequestParam(value = "promoVideo", required = false) MultipartFile promoVideo,
            @RequestParam(value = "resourceFiles", required = false) MultipartFile[] resourceFiles,
            @RequestParam(value = "resourceTitles", required = false) String[] resourceTitles,
            @RequestParam(value = "resourceTypes", required = false) String[] resourceTypes,
            @RequestParam(value = "resourceDescriptions", required = false) String[] resourceDescriptions,
            @RequestParam Map<String, String> allParams,
            Principal principal,
            RedirectAttributes redirectAttributes) {

        String email = principal.getName();
        User instructor = userService.findByEmail(email);

        Course targetCourse;
        boolean isNew = (course.getId() == null);

        if (isNew) {
            targetCourse = course;
            targetCourse.setInstructor(instructor);
            targetCourse.setViewCount(0L);
        } else {
            // Edit mode: verify ownership
            targetCourse = getOwnedCourseOrThrow(course.getId(), email);
            // Copy editable fields
            targetCourse.setTitle(course.getTitle());
            targetCourse.setSubtitle(course.getSubtitle());
            targetCourse.setDescription(course.getDescription());
            targetCourse.setLongDescription(course.getLongDescription());
            targetCourse.setCategory(course.getCategory());
            targetCourse.setSubCategory(course.getSubCategory());
            targetCourse.setLevel(course.getLevel());
            targetCourse.setLanguage(course.getLanguage());
            targetCourse.setFormat(course.getFormat());
            targetCourse.setTeachingMethod(course.getTeachingMethod());
            targetCourse.setTags(course.getTags());
            targetCourse.setPrice(course.getPrice());
            targetCourse.setSalePrice(course.getSalePrice());
            targetCourse.setLearningObjectives(course.getLearningObjectives());
            targetCourse.setRequirements(course.getRequirements());
            targetCourse.setTargetAudience(course.getTargetAudience());
            targetCourse.setRequiredExperience(course.getRequiredExperience());
            targetCourse.setExpectedSkills(course.getExpectedSkills());
            targetCourse.setAgeRange(course.getAgeRange());
            targetCourse.setAccessibilityRequirements(course.getAccessibilityRequirements());
            targetCourse.setAccessDuration(course.getAccessDuration());
        }

        // Process thumbnail
        if (thumbnail != null && !thumbnail.isEmpty()) {
            targetCourse.setImageUrl(fileStorageService.store(thumbnail));
        } else if (targetCourse.getImageUrl() == null || targetCourse.getImageUrl().trim().isEmpty()) {
            targetCourse.setImageUrl("https://img-c.udemycdn.com/course/480x270/placeholder.jpg");
        }

        // Process promotional video
        if (promoVideo != null && !promoVideo.isEmpty()) {
            targetCourse.setVideoUrl(fileStorageService.store(promoVideo));
        }

        targetCourse.setLastUpdated(LocalDateTime.now().toLocalDate().toString());

        // Extract Category-Specific Fields from request
        Map<String, String> catDataMap = new HashMap<>();
        for (Map.Entry<String, String> entry : allParams.entrySet()) {
            String key = entry.getKey();
            if (key.startsWith("catData_")) {
                String cleanKey = key.substring("catData_".length());
                if (entry.getValue() != null && !entry.getValue().trim().isEmpty()) {
                    catDataMap.put(cleanKey, entry.getValue().trim());
                }
            }
        }
        targetCourse.setCategoryDataMap(catDataMap);

        // Validation rule: Must start with a letter for titles/descriptions if provided
        Predicate<String> startsWithLetter = s -> s != null && !s.trim().isEmpty() && Character.isLetter(s.trim().charAt(0));

        if (!startsWithLetter.test(targetCourse.getTitle())) {
            redirectAttributes.addFlashAttribute("error", "Course Title must start with a letter.");
            return isNew ? "redirect:/vendor/courses/create" : "redirect:/vendor/courses/" + targetCourse.getId() + "/edit";
        }

        if (targetCourse.getDescription() != null && !targetCourse.getDescription().trim().isEmpty() && !startsWithLetter.test(targetCourse.getDescription())) {
            redirectAttributes.addFlashAttribute("error", "Course Description must start with a letter.");
            return isNew ? "redirect:/vendor/courses/create" : "redirect:/vendor/courses/" + targetCourse.getId() + "/edit";
        }

        // Curriculum sections & lectures processing if supplied in form
        if (course.getSections() != null && !course.getSections().isEmpty()) {
            for (CourseSection section : course.getSections()) {
                if (section.getTitle() != null && !section.getTitle().trim().isEmpty()) {
                    section.setCourse(targetCourse);
                    if (section.getLectures() != null) {
                        for (Lecture lecture : section.getLectures()) {
                            if (lecture.getTitle() != null && !lecture.getTitle().trim().isEmpty()) {
                                lecture.setSection(section);
                                if (lecture.getVideoFile() != null && !lecture.getVideoFile().isEmpty()) {
                                    String storedUrl = fileStorageService.store(lecture.getVideoFile());
                                    lecture.setVideoUrl(storedUrl);
                                }
                            }
                        }
                    }
                }
            }
            targetCourse.setSections(course.getSections());
        }

        // Process Downloadable Course Resources if uploaded
        if (resourceFiles != null && resourceFiles.length > 0) {
            for (int i = 0; i < resourceFiles.length; i++) {
                MultipartFile resFile = resourceFiles[i];
                if (resFile != null && !resFile.isEmpty()) {
                    String storedPath = fileStorageService.store(resFile);
                    String resTitle = (resourceTitles != null && resourceTitles.length > i && resourceTitles[i] != null && !resourceTitles[i].trim().isEmpty())
                            ? resourceTitles[i].trim() : resFile.getOriginalFilename();
                    String resType = (resourceTypes != null && resourceTypes.length > i && resourceTypes[i] != null && !resourceTypes[i].trim().isEmpty())
                            ? resourceTypes[i].trim() : "RESOURCE";
                    String resDesc = (resourceDescriptions != null && resourceDescriptions.length > i && resourceDescriptions[i] != null)
                            ? resourceDescriptions[i].trim() : "";

                    CourseResource cr = new CourseResource(resTitle, resDesc, storedPath, resType, resFile.getSize(), targetCourse);
                    targetCourse.addResource(cr);
                }
            }
        }

        // Action Decision: "submit" (submit for review) vs "draft" (save draft)
        if ("submit".equalsIgnoreCase(action)) {
            // Validate required fields for submission
            if (targetCourse.getDescription() == null || targetCourse.getDescription().trim().isEmpty()) {
                redirectAttributes.addFlashAttribute("error", "Course description is required to submit for review.");
                return isNew ? "redirect:/vendor/courses/create" : "redirect:/vendor/courses/" + targetCourse.getId() + "/edit";
            }
            if (targetCourse.getCategory() == null || targetCourse.getCategory().getId() == null) {
                redirectAttributes.addFlashAttribute("error", "Please select a Category before submitting.");
                return isNew ? "redirect:/vendor/courses/create" : "redirect:/vendor/courses/" + targetCourse.getId() + "/edit";
            }

            targetCourse.setStatus(CourseStatus.PENDING_REVIEW);
            targetCourse.setPublished(false);
            targetCourse.setSubmittedAt(LocalDateTime.now());
            targetCourse.setRejectionReason(null); // Clear previous rejection reason upon resubmission
            courseService.saveCourse(targetCourse);
            redirectAttributes.addFlashAttribute("success", "Course '" + targetCourse.getTitle() + "' submitted for admin review successfully!");
        } else {
            // Save as Draft
            if (targetCourse.getStatus() != CourseStatus.PENDING_REVIEW && targetCourse.getStatus() != CourseStatus.PUBLISHED) {
                targetCourse.setStatus(CourseStatus.DRAFT);
            }
            targetCourse.setPublished(false);
            courseService.saveCourse(targetCourse);
            redirectAttributes.addFlashAttribute("success", "Course draft saved successfully!");
        }

        return "redirect:/vendor/dashboard?tab=courses";
    }

    @PostMapping("/courses/{id}/submit")
    public String submitCourseForReview(@PathVariable("id") Long id, Principal principal, RedirectAttributes redirectAttributes) {
        String email = principal.getName();
        Course course = getOwnedCourseOrThrow(id, email);

        if (course.getTitle() == null || course.getTitle().trim().isEmpty() ||
            course.getDescription() == null || course.getDescription().trim().isEmpty()) {
            redirectAttributes.addFlashAttribute("error", "Course title and description are required before submitting.");
            return "redirect:/vendor/courses/" + id + "/edit";
        }

        courseService.submitCourseForReview(id);
        redirectAttributes.addFlashAttribute("success", "Course submitted for admin review!");
        return "redirect:/vendor/dashboard?tab=courses";
    }

    @PostMapping("/courses/{id}/status")
    @ResponseBody
    public ResponseEntity<?> updateCourseStatus(@PathVariable("id") Long id,
            @RequestParam("published") boolean published, Principal principal) {
        try {
            getOwnedCourseOrThrow(id, principal.getName());
            if (published) {
                courseService.publishCourse(id);
            } else {
                courseService.unpublishCourse(id);
            }
            return ResponseEntity.ok().build();
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @PostMapping("/courses/{id}/delete")
    public String deleteCourse(@PathVariable("id") Long id, Principal principal,
            RedirectAttributes redirectAttributes) {
        try {
            getOwnedCourseOrThrow(id, principal.getName());
            courseService.deleteCourse(id);
            redirectAttributes.addFlashAttribute("success", "Course deleted successfully!");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", "Unable to delete course: " + e.getMessage());
        }
        return "redirect:/vendor/dashboard?tab=courses";
    }

    @PostMapping("/courses/{id}/sections/add")
    public String addSection(@PathVariable("id") Long id, @RequestParam("title") String title,
            Principal principal, RedirectAttributes redirectAttributes) {
        getOwnedCourseOrThrow(id, principal.getName());
        courseService.addSectionToCourse(id, title);
        redirectAttributes.addFlashAttribute("success", "Section added successfully!");
        return "redirect:/vendor/courses/" + id + "/edit";
    }

    @PostMapping("/sections/{id}/lectures/add")
    public String addLecture(@PathVariable("id") Long id,
            @RequestParam("title") String title,
            @RequestParam("durationSeconds") Integer durationSeconds,
            @RequestParam(value = "videoFile", required = false) MultipartFile videoFile,
            @RequestParam(value = "videoUrl", required = false) String videoUrl,
            @RequestParam(value = "content", required = false) String content,
            @RequestParam(value = "isPreview", defaultValue = "false") boolean isPreview,
            @RequestHeader(value = "Referer", required = false) String referer,
            Principal principal,
            RedirectAttributes redirectAttributes) {

        CourseSection section = courseSectionRepository.findById(id).orElse(null);
        if (section == null) {
            redirectAttributes.addFlashAttribute("error", "Section not found.");
            return "redirect:/vendor/dashboard";
        }
        // Verify ownership
        getOwnedCourseOrThrow(section.getCourse().getId(), principal.getName());

        Lecture lecture = new Lecture();
        lecture.setTitle(title);
        lecture.setDurationSeconds(durationSeconds != null ? durationSeconds : 0);
        lecture.setContent(content);
        lecture.setPreview(isPreview);

        if (videoFile != null && !videoFile.isEmpty()) {
            String fileUrl = fileStorageService.store(videoFile);
            lecture.setVideoUrl(fileUrl);
        } else if (videoUrl != null && !videoUrl.trim().isEmpty()) {
            lecture.setVideoUrl(videoUrl.trim());
        }

        courseService.addLectureToSection(id, lecture);
        courseService.updateCourseStats(section.getCourse().getId());

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

        if (profilePicture != null && !profilePicture.isEmpty()) {
            try {
                String imagePath = fileStorageService.store(profilePicture);
                user.setProfileImageUrl(imagePath);
            } catch (Exception e) {
                redirectAttributes.addFlashAttribute("error", "Failed to upload image: " + e.getMessage());
                return "redirect:/vendor/dashboard?tab=profile";
            }
        }

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
            Principal principal, RedirectAttributes redirectAttributes) {
        Review review = reviewRepository.findById(id).orElse(null);
        if (review != null) {
            getOwnedCourseOrThrow(review.getCourse().getId(), principal.getName());
            review.setReply(reply);
            review.setReplyDate(LocalDateTime.now());
            reviewRepository.save(review);
            redirectAttributes.addFlashAttribute("success", "Reply sent!");
        }
        return "redirect:/vendor/dashboard?tab=communications";
    }

    @PostMapping("/questions/{id}/reply")
    public String replyToQuestion(@PathVariable("id") Long id, @RequestParam("content") String content,
            Principal principal, RedirectAttributes redirectAttributes) {
        CourseQuestion question = courseQuestionRepository.findById(id).orElse(null);
        if (question != null) {
            getOwnedCourseOrThrow(question.getCourse().getId(), principal.getName());
            User instructor = userService.findByEmail(principal.getName());
            CourseAnswer answer = new CourseAnswer();
            answer.setQuestion(question);
            answer.setUser(instructor);
            answer.setContent(content);
            courseAnswerRepository.save(answer);
            redirectAttributes.addFlashAttribute("success", "Answer posted successfully!");
        }
        return "redirect:/vendor/dashboard?tab=communications";
    }

    @PostMapping("/enrollments/{id}/certificate")
    public String generateCertificate(@PathVariable("id") Long id, Principal principal, RedirectAttributes redirectAttributes) {
        Enrollment enrollment = enrollmentRepository.findById(id).orElse(null);
        if (enrollment != null) {
            getOwnedCourseOrThrow(enrollment.getCourse().getId(), principal.getName());
            enrollment.setExamPassed(true);
            enrollment.setCertificateDate(LocalDateTime.now());
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

    @GetMapping("/api/exams-stats")
    @ResponseBody
    public ResponseEntity<Map<Long, Integer>> getExamsStats(Principal principal) {
        if (principal == null)
            return ResponseEntity.status(401).build();
        String email = principal.getName();
        List<Course> courses = courseService.getCoursesByInstructor(email);
        Map<Long, Integer> stats = new HashMap<>();
        for (Course course : courses) {
            stats.put(course.getId(), course.getExamQuestions() != null ? course.getExamQuestions().size() : 0);
        }
        return ResponseEntity.ok(stats);
    }

    @GetMapping("/courses/{id}/questions")
    public String manageExamQuestions(@PathVariable("id") Long id, Model model, Principal principal) {
        Course course = getOwnedCourseOrThrow(id, principal.getName());

        model.addAttribute("course", course);
        model.addAttribute("questions", course.getExamQuestions());
        User instructor = userService.findByEmail(principal.getName());
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
        Course course = getOwnedCourseOrThrow(id, principal.getName());

        ExamQuestion q = new ExamQuestion(course, questionText, optionA, optionB, optionC, optionD, correctOption);
        course.getExamQuestions().add(q);
        courseService.saveCourse(course);
        return "redirect:/vendor/courses/" + id + "/questions";
    }

    @PostMapping("/courses/{id}/questions/delete/{qid}")
    public String deleteExamQuestion(@PathVariable("id") Long id,
            @PathVariable("qid") Long qid,
            Principal principal) {
        Course course = getOwnedCourseOrThrow(id, principal.getName());

        course.getExamQuestions().removeIf(q -> q.getId().equals(qid));
        courseService.saveCourse(course);
        return "redirect:/vendor/courses/" + id + "/questions";
    }
}
