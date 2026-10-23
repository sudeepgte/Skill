package com.skillhub.controller;

import com.skillhub.model.*;
import com.skillhub.repository.*;
import com.skillhub.service.CategoryService;
import com.skillhub.service.CourseService;
import com.skillhub.service.GeminiService;
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
import java.util.stream.Collectors;
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

    @Autowired
    private GeminiService geminiService;

    @Autowired
    private ExamQuestionRepository examQuestionRepository;

    // Helper method to enforce ownership validation
    private Course getOwnedCourseOrThrow(Long courseId, String email) {
        Course course = courseService.getCourseById(courseId);
        if (course == null) {
            throw new NoSuchElementException("Course not found with id: " + courseId);
        }
        User user = userService.findByEmail(email);
        if (user != null && ("ADMIN".equalsIgnoreCase(user.getRole()) || "ROLE_ADMIN".equalsIgnoreCase(user.getRole()))) {
            return course;
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

        long totalStudents = enrollmentRepository.countByInstructorEmail(email);
        Long viewsObj = courseService.sumViewsByInstructorEmail(email);
        long totalViews = viewsObj != null ? viewsObj : 0L;
        long totalExamsPassed = enrollmentRepository.countExamsPassedByInstructorEmail(email);

        double totalRating = 0.0;
        int totalReviews = 0;

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

        if ("communications".equals(tab) || "messages".equals(tab)) {
            model.addAttribute("reviews", reviewRepository.findByInstructorEmail(email));
            model.addAttribute("questions", questionsList);
            List<User> students = userService.getAllUsers().stream()
                    .filter(u -> !"INSTRUCTOR".equals(u.getRole()) && !"ADMIN".equals(u.getRole()))
                    .toList();
            model.addAttribute("chatStudents", students);
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

    /** REST endpoint â€“ returns live progress for all enrolled students (JSON) */
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

        long totalEnrolled = enrollmentRepository.countByInstructorEmail(email);
        Long viewsObj = courseService.sumViewsByInstructorEmail(email);
        long totalViews = viewsObj != null ? viewsObj : 0L;
        long totalExamsPassed = enrollmentRepository.countExamsPassedByInstructorEmail(email);

        double totalRating = 0.0;
        int totalReviews = 0;

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
        List<Category> allCategories = categoryService.getAllCategories();
        Category selectedCategory = null;

        if (principal != null) {
            User instructor = userService.findByEmail(principal.getName());
            if (instructor != null) {
                model.addAttribute("instructor", instructor);
                model.addAttribute("vendor", instructor);
                model.addAttribute("user", instructor);
                String expertise = instructor.getSkills() != null && !instructor.getSkills().isBlank()
                        ? instructor.getSkills()
                        : instructor.getExpertise();
                if (expertise != null && !expertise.isBlank()) {
                    for (Category cat : allCategories) {
                        if (cat.getName() != null && (cat.getName().equalsIgnoreCase(expertise.trim())
                                || cat.getName().toLowerCase().contains(expertise.trim().toLowerCase())
                                || expertise.trim().toLowerCase().contains(cat.getName().toLowerCase()))) {
                            selectedCategory = cat;
                            break;
                        }
                    }
                }
            }
        }

        if (selectedCategory != null) {
            course.setCategory(selectedCategory);
            model.addAttribute("defaultCategoryId", selectedCategory.getId());
            model.addAttribute("defaultCategoryName", selectedCategory.getName());
        } else {
            course.setCategory(new Category());
            model.addAttribute("defaultCategoryId", "");
            model.addAttribute("defaultCategoryName", "");
        }
        course.setPrice(0.0);
        course.setStatus(CourseStatus.DRAFT);
        course.setFormat("Video Course");
        course.setTeachingMethod("Self-paced");
        course.setLevel("Beginner");
        course.setLanguage("English");
        course.setAccessDuration("Lifetime Access");

        model.addAttribute("course", course);
        if (course.getCategory() != null && course.getCategory().getId() != null) {
            model.addAttribute("defaultCategoryId", course.getCategory().getId());
            model.addAttribute("defaultCategoryName", course.getCategory().getName());
        } else {
            model.addAttribute("defaultCategoryId", "");
            model.addAttribute("defaultCategoryName", "");
        }
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
        User instructor = userService.findByEmail(email);
        if (instructor != null) {
            model.addAttribute("instructor", instructor);
            model.addAttribute("vendor", instructor);
            model.addAttribute("user", instructor);
        }

        model.addAttribute("course", course);
        if (course.getCategory() != null && course.getCategory().getId() != null) {
            model.addAttribute("defaultCategoryId", course.getCategory().getId());
            model.addAttribute("defaultCategoryName", course.getCategory().getName());
        } else {
            model.addAttribute("defaultCategoryId", "");
            model.addAttribute("defaultCategoryName", "");
        }
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

        if (course.getPrice() != null && course.getPrice() < 0) {
            redirectAttributes.addFlashAttribute("error", "Course price cannot be negative.");
            return "redirect:/vendor/courses/create";
        }

        // Validate file types before processing
        if (thumbnail != null && !thumbnail.isEmpty()) {
            String mimeType = thumbnail.getContentType();
            if (mimeType == null || !mimeType.startsWith("image/")) {
                redirectAttributes.addFlashAttribute("error", "Thumbnail must be an image file.");
                return "redirect:/vendor/courses/create";
            }
        }
        if (promoVideo != null && !promoVideo.isEmpty()) {
            String mimeType = promoVideo.getContentType();
            if (mimeType == null || !mimeType.startsWith("video/")) {
                redirectAttributes.addFlashAttribute("error", "Promotional video must be a valid video file.");
                return "redirect:/vendor/courses/create";
            }
        }

        if (course.getId() == null) {
            course.setPublished(false);
            course.setStatus(CourseStatus.DRAFT);
        }
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

        // Process thumbnail (only once on targetCourse)
        if (thumbnail != null && !thumbnail.isEmpty()) {
            targetCourse.setImageUrl(fileStorageService.store(thumbnail));
        } else if (isNew || targetCourse.getImageUrl() == null || targetCourse.getImageUrl().trim().isEmpty() || targetCourse.getImageUrl().contains("udemycdn.com")) {
            targetCourse.setImageUrl("/images/course-placeholder.jpg");
        }

        // Process promotional video (only once on targetCourse)
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


        // Process Curriculum Videos and Validate Titles
        if (course.getSections() != null && !course.getSections().isEmpty()) {
            List<CourseSection> validSections = new ArrayList<>();
            for (CourseSection section : course.getSections()) {
                if (section.getTitle() != null && !section.getTitle().trim().isEmpty()) {
                    if (!startsWithLetter.test(section.getTitle())) {
                        redirectAttributes.addFlashAttribute("error", "Section titles must start with a letter.");
                        return isNew ? "redirect:/vendor/courses/create" : "redirect:/vendor/courses/" + targetCourse.getId() + "/edit";
                    }
                    section.setCourse(targetCourse);
                    if (section.getLectures() != null) {
                        List<Lecture> validLectures = new ArrayList<>();
                        for (Lecture lecture : section.getLectures()) {
                            if (lecture.getTitle() != null && !lecture.getTitle().trim().isEmpty()) {
                                if (!startsWithLetter.test(lecture.getTitle())) {
                                    redirectAttributes.addFlashAttribute("error", "Lecture titles must start with a letter.");
                                    return isNew ? "redirect:/vendor/courses/create" : "redirect:/vendor/courses/" + targetCourse.getId() + "/edit";
                                }
                                if (lecture.getVideoFile() != null && !lecture.getVideoFile().isEmpty()) {
                                    String lecMimeType = lecture.getVideoFile().getContentType();
                                    if (lecMimeType == null || !lecMimeType.startsWith("video/")) {
                                        redirectAttributes.addFlashAttribute("error", "Lecture video must be a valid video file.");
                                        return isNew ? "redirect:/vendor/courses/create" : "redirect:/vendor/courses/" + targetCourse.getId() + "/edit";
                                    }
                                    lecture.setVideoUrl(fileStorageService.store(lecture.getVideoFile()));
                                }
                                lecture.setSection(section);
                                validLectures.add(lecture);
                            }
                        }
                        section.setLectures(validLectures);
                    }
                    validSections.add(section);
                }
            }

            if (isNew) {
                targetCourse.setSections(validSections);
            } else {
                syncCourseCurriculum(targetCourse, validSections);
            }
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
            @RequestParam(value = "phone", required = false) String phone,
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

        if (name == null || name.trim().isEmpty()) {
            redirectAttributes.addFlashAttribute("error", "Full Name is required and cannot be empty.");
            return "redirect:/vendor/dashboard?tab=profile";
        }
        if (name.trim().length() < 2) {
            redirectAttributes.addFlashAttribute("error", "Full Name must be at least 2 characters long.");
            return "redirect:/vendor/dashboard?tab=profile";
        }
        if (name.trim().length() > 60) {
            redirectAttributes.addFlashAttribute("error", "Full Name cannot exceed 60 characters.");
            return "redirect:/vendor/dashboard?tab=profile";
        }

        user.setName(name.trim());
        if (phone != null && !phone.trim().isEmpty()) {
            String cleanPhone = phone.trim();
            String digitsOnly = cleanPhone.replaceAll("[^0-9]", "");
            if (digitsOnly.length() < 10 || digitsOnly.length() > 15) {
                redirectAttributes.addFlashAttribute("error", "Phone number must contain between 10 and 15 digits.");
                return "redirect:/vendor/dashboard?tab=profile";
            }
            user.setPhone(cleanPhone);
        } else {
            user.setPhone(null);
        }

        if (expertise != null && expertise.length() > 100) {
            user.setExpertise(expertise.substring(0, 100));
        } else {
            user.setExpertise(expertise != null ? expertise.trim() : null);
        }

        if (skills != null && skills.length() > 250) {
            user.setSkills(skills.substring(0, 250));
        } else {
            user.setSkills(skills != null ? skills.trim() : null);
        }

        if (bio != null && bio.length() > 1500) {
            user.setBio(bio.substring(0, 1500));
        } else {
            user.setBio(bio != null ? bio.trim() : null);
        }

        user.setInstagramUrl(instagramUrl);
        user.setLinkedinUrl(linkedinUrl);
        user.setYoutubeUrl(youtubeUrl);

        if (profilePicture != null && !profilePicture.isEmpty()) {
            try {
                String contentType = profilePicture.getContentType();
                if (contentType != null && !contentType.startsWith("image/")) {
                    redirectAttributes.addFlashAttribute("error", "Invalid file type. Only image files (JPG, PNG, GIF, WEBP) are allowed.");
                    return "redirect:/vendor/dashboard?tab=profile";
                }
                if (profilePicture.getSize() > 5 * 1024 * 1024) {
                    redirectAttributes.addFlashAttribute("error", "Profile picture exceeds maximum allowed size of 5MB.");
                    return "redirect:/vendor/dashboard?tab=profile";
                }
                String imagePath = fileStorageService.store(profilePicture);
                user.setProfileImageUrl(imagePath);
            } catch (Exception e) {
                redirectAttributes.addFlashAttribute("error", "Failed to upload image: " + e.getMessage());
                return "redirect:/vendor/dashboard?tab=profile";
            }
        }

        boolean isPasswordChangeRequested = (currentPassword != null && !currentPassword.trim().isEmpty())
                || (newPassword != null && !newPassword.trim().isEmpty())
                || (confirmPassword != null && !confirmPassword.trim().isEmpty());

        if (isPasswordChangeRequested) {
            if (currentPassword == null || currentPassword.trim().isEmpty()) {
                redirectAttributes.addFlashAttribute("error", "Current password is required to update your login password.");
                return "redirect:/vendor/dashboard?tab=profile";
            }
            if (!passwordEncoder.matches(currentPassword, user.getPassword())) {
                redirectAttributes.addFlashAttribute("error", "Incorrect current password. Please try again.");
                return "redirect:/vendor/dashboard?tab=profile";
            }
            if (newPassword == null || newPassword.trim().isEmpty()) {
                redirectAttributes.addFlashAttribute("error", "New password is required.");
                return "redirect:/vendor/dashboard?tab=profile";
            }
            if (newPassword.length() < 6) {
                redirectAttributes.addFlashAttribute("error", "New password must be at least 6 characters long.");
                return "redirect:/vendor/dashboard?tab=profile";
            }
            if (newPassword.equals(currentPassword)) {
                redirectAttributes.addFlashAttribute("error", "New password cannot be the same as your current password.");
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
        List<Course> courses;
        if (instructor != null && ("ADMIN".equalsIgnoreCase(instructor.getRole()) || "ROLE_ADMIN".equalsIgnoreCase(instructor.getRole()))) {
            courses = courseService.getAllCourses();
        } else {
            courses = courseService.getCoursesByInstructor(email);
        }

        for (Course c : courses) {
            if (c.getExamQuestions() != null && !c.getExamQuestions().isEmpty() && c.getExamUpdatedAt() == null) {
                c.setExamUpdatedAt(c.getSubmittedAt() != null ? c.getSubmittedAt() : LocalDateTime.now());
                courseService.saveCourse(c);
            }
        }

        List<Course> addedExams = courses.stream()
                .filter(c -> c.getExamQuestions() != null && !c.getExamQuestions().isEmpty())
                .sorted((c1, c2) -> {
                    LocalDateTime t1 = c1.getExamUpdatedAt() != null ? c1.getExamUpdatedAt() :
                            (c1.getSubmittedAt() != null ? c1.getSubmittedAt() : LocalDateTime.MIN);
                    LocalDateTime t2 = c2.getExamUpdatedAt() != null ? c2.getExamUpdatedAt() :
                            (c2.getSubmittedAt() != null ? c2.getSubmittedAt() : LocalDateTime.MIN);
                    return t2.compareTo(t1);
                })
                .collect(Collectors.toList());

        model.addAttribute("courses", courses);
        model.addAttribute("addedExams", addedExams);
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
        User user = userService.findByEmail(email);
        List<Course> courses;
        if (user != null && ("ADMIN".equalsIgnoreCase(user.getRole()) || "ROLE_ADMIN".equalsIgnoreCase(user.getRole()))) {
            courses = courseService.getAllCourses();
        } else {
            courses = courseService.getCoursesByInstructor(email);
        }
        Map<Long, Integer> stats = new HashMap<>();
        for (Course course : courses) {
            stats.put(course.getId(), course.getExamQuestions() != null ? course.getExamQuestions().size() : 0);
        }
        return ResponseEntity.ok(stats);
    }

    private String resolveCourseTopic(Course course) {
        if (course.getCategory() != null && course.getCategory().getName() != null && !course.getCategory().getName().isBlank()) {
            String topic = course.getCategory().getName().trim();
            if (course.getSubCategory() != null && !course.getSubCategory().isBlank()) {
                topic += " - " + course.getSubCategory().trim();
            }
            return topic;
        }
        if (course.getTitle() != null && !course.getTitle().isBlank()) {
            return course.getTitle().trim();
        }
        return "General";
    }

    @GetMapping("/courses/{id}/questions")
    public String manageExamQuestions(@PathVariable("id") Long id, Model model, Principal principal) {
        Course course = getOwnedCourseOrThrow(id, principal.getName());

        model.addAttribute("course", course);
        model.addAttribute("questions", course.getExamQuestions());
        User instructor = userService.findByEmail(principal.getName());
        model.addAttribute("instructor", instructor);
        model.addAttribute("vendor", instructor);

        String defaultTopic = resolveCourseTopic(course);
        model.addAttribute("defaultTopic", defaultTopic);

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
        course.setExamUpdatedAt(LocalDateTime.now());
        courseService.saveCourse(course);
        return "redirect:/vendor/courses/" + id + "/questions";
    }

    @PostMapping("/courses/{id}/questions/generate-ai")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> generateQuestionsAi(
            @PathVariable("id") Long id,
            @RequestParam(value = "count", required = false) Integer count,
            Principal principal) {
        if (count == null || count <= 0) {
            Map<String, Object> err = new HashMap<>();
            err.put("success", false);
            err.put("message", "Number of questions cannot be 0 or empty. Please enter any number greater than 0.");
            return ResponseEntity.badRequest().body(err);
        }
        Course course = getOwnedCourseOrThrow(id, principal.getName());

        // Strictly enforce the registered course category/topic (cannot be modified)
        String topic = resolveCourseTopic(course);

        int targetCount = count;
        List<Map<String, String>> generatedList = geminiService.generateMcqQuestions(topic, targetCount);

        // REPLACE existing questions: completely remove existing questions first
        if (course.getExamQuestions() != null) {
            course.getExamQuestions().clear();
            courseService.saveCourse(course);
        }
        examQuestionRepository.deleteByCourseId(course.getId());

        if (course.getExamQuestions() == null) {
            course.setExamQuestions(new ArrayList<>());
        }

        List<Map<String, Object>> savedResults = new ArrayList<>();
        for (Map<String, String> item : generatedList) {
            ExamQuestion q = new ExamQuestion(
                    course,
                    item.get("questionText"),
                    item.get("optionA"),
                    item.get("optionB"),
                    item.get("optionC"),
                    item.get("optionD"),
                    item.get("correctOption")
            );
            q.setCourse(course);
            q = examQuestionRepository.save(q);
            course.getExamQuestions().add(q);

            Map<String, Object> dto = new HashMap<>();
            dto.put("id", q.getId());
            dto.put("questionText", q.getQuestionText());
            dto.put("optionA", q.getOptionA());
            dto.put("optionB", q.getOptionB());
            dto.put("optionC", q.getOptionC());
            dto.put("optionD", q.getOptionD());
            dto.put("correctOption", q.getCorrectOption());
            savedResults.add(dto);
        }
        course.setExamUpdatedAt(LocalDateTime.now());
        courseService.saveCourse(course);

        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("message", "Successfully generated and replaced with " + savedResults.size() + " questions!");
        response.put("topic", topic);
        response.put("count", savedResults.size());
        response.put("totalQuestions", course.getExamQuestions().size());
        response.put("questions", savedResults);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/courses/{id}/questions/generate-ai-form")
    public String generateQuestionsAiForm(
            @PathVariable("id") Long id,
            @RequestParam(value = "count", required = false) Integer count,
            Principal principal,
            RedirectAttributes redirectAttributes) {
        if (count == null || count <= 0) {
            redirectAttributes.addFlashAttribute("error", "Number of questions cannot be 0 or empty. Please enter any number greater than 0.");
            return "redirect:/vendor/courses/" + id + "/questions";
        }
        Course course = getOwnedCourseOrThrow(id, principal.getName());

        String topic = resolveCourseTopic(course);
        int targetCount = count;
        List<Map<String, String>> generatedList = geminiService.generateMcqQuestions(topic, targetCount);

        // REPLACE existing questions: completely remove existing questions first
        if (course.getExamQuestions() != null) {
            course.getExamQuestions().clear();
            courseService.saveCourse(course);
        }
        examQuestionRepository.deleteByCourseId(course.getId());

        if (course.getExamQuestions() == null) {
            course.setExamQuestions(new ArrayList<>());
        }

        for (Map<String, String> item : generatedList) {
            ExamQuestion q = new ExamQuestion(
                    course,
                    item.get("questionText"),
                    item.get("optionA"),
                    item.get("optionB"),
                    item.get("optionC"),
                    item.get("optionD"),
                    item.get("correctOption")
            );
            q.setCourse(course);
            q = examQuestionRepository.save(q);
            course.getExamQuestions().add(q);
        }
        course.setExamUpdatedAt(LocalDateTime.now());
        courseService.saveCourse(course);

        redirectAttributes.addFlashAttribute("success", "Successfully generated and replaced with " + generatedList.size() + " questions for topic: " + topic);
        return "redirect:/vendor/courses/" + id + "/questions";
    }

    @PostMapping("/courses/{id}/questions/clear-all")
    public String clearAllExamQuestions(@PathVariable("id") Long id, Principal principal, RedirectAttributes redirectAttributes) {
        Course course = getOwnedCourseOrThrow(id, principal.getName());
        if (course.getExamQuestions() != null) {
            course.getExamQuestions().clear();
        }
        course.setExamUpdatedAt(null);
        courseService.saveCourse(course);
        examQuestionRepository.deleteByCourseId(course.getId());
        redirectAttributes.addFlashAttribute("success", "All exam questions have been cleared.");
        return "redirect:/vendor/courses/" + id + "/questions";
    }

    @PostMapping("/courses/{id}/questions/clear-all-ajax")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> clearAllExamQuestionsAjax(@PathVariable("id") Long id, Principal principal) {
        Course course = getOwnedCourseOrThrow(id, principal.getName());
        if (course.getExamQuestions() != null) {
            course.getExamQuestions().clear();
        }
        course.setExamUpdatedAt(null);
        courseService.saveCourse(course);
        examQuestionRepository.deleteByCourseId(course.getId());

        Map<String, Object> resp = new HashMap<>();
        resp.put("success", true);
        resp.put("message", "All exam questions have been cleared.");
        resp.put("totalQuestions", 0);
        return ResponseEntity.ok(resp);
    }

    @PostMapping("/courses/{id}/questions/delete/{qid}")
    public String deleteExamQuestion(@PathVariable("id") Long id,
            @PathVariable("qid") Long qid,
            Principal principal) {
        Course course = getOwnedCourseOrThrow(id, principal.getName());

        course.getExamQuestions().removeIf(q -> q.getId().equals(qid));
        if (course.getExamQuestions().isEmpty()) {
            course.setExamUpdatedAt(null);
        } else {
            course.setExamUpdatedAt(LocalDateTime.now());
        }
        courseService.saveCourse(course);
        return "redirect:/vendor/courses/" + id + "/questions";
    }

    private void syncCourseCurriculum(Course targetCourse, List<CourseSection> incomingSections) {
        if (incomingSections == null || incomingSections.isEmpty()) {
            targetCourse.getSections().clear();
            return;
        }

        Map<Long, CourseSection> existingMap = new HashMap<>();
        for (CourseSection sec : targetCourse.getSections()) {
            if (sec.getId() != null) {
                existingMap.put(sec.getId(), sec);
            }
        }

        Set<Long> incomingIds = incomingSections.stream()
                .map(CourseSection::getId)
                .filter(Objects::nonNull)
                .collect(java.util.stream.Collectors.toSet());

        targetCourse.getSections().removeIf(s -> s.getId() != null && !incomingIds.contains(s.getId()));

        for (CourseSection incoming : incomingSections) {
            if (incoming.getId() != null && existingMap.containsKey(incoming.getId())) {
                CourseSection existing = existingMap.get(incoming.getId());
                existing.setTitle(incoming.getTitle());
                existing.setPosition(incoming.getPosition());
                syncSectionLectures(existing, incoming.getLectures());
            } else {
                incoming.setCourse(targetCourse);
                targetCourse.getSections().add(incoming);
            }
        }
    }

    private void syncSectionLectures(CourseSection existingSection, List<Lecture> incomingLectures) {
        if (incomingLectures == null || incomingLectures.isEmpty()) {
            existingSection.getLectures().clear();
            return;
        }

        Map<Long, Lecture> existingMap = new HashMap<>();
        for (Lecture lec : existingSection.getLectures()) {
            if (lec.getId() != null) {
                existingMap.put(lec.getId(), lec);
            }
        }

        Set<Long> incomingIds = incomingLectures.stream()
                .map(Lecture::getId)
                .filter(Objects::nonNull)
                .collect(java.util.stream.Collectors.toSet());

        existingSection.getLectures().removeIf(l -> l.getId() != null && !incomingIds.contains(l.getId()));

        for (Lecture incoming : incomingLectures) {
            if (incoming.getId() != null && existingMap.containsKey(incoming.getId())) {
                Lecture existing = existingMap.get(incoming.getId());
                existing.setTitle(incoming.getTitle());
                existing.setPosition(incoming.getPosition());
                existing.setDurationSeconds(incoming.getDurationSeconds());
                if (incoming.getVideoUrl() != null && !incoming.getVideoUrl().isEmpty()) {
                    existing.setVideoUrl(incoming.getVideoUrl());
                }
            } else {
                incoming.setSection(existingSection);
                existingSection.getLectures().add(incoming);
            }
        }
    }
}
