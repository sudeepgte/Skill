package com.skillhub.controller;

import com.skillhub.model.*;
import com.skillhub.model.enums.CourseQuestionType;
import com.skillhub.repository.*;
import com.skillhub.service.*;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.security.Principal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Controller
@RequestMapping("/admin")
@PreAuthorize("hasRole('ADMIN')")
public class AdminController {

    @Autowired
    private UserService userService;

    @Autowired
    private CourseService courseService;

    @Autowired
    private LectureRepository lectureRepository;

    @Autowired
    private ReportRepository reportRepository;

    @Autowired
    private CourseRepository courseRepository;

    @Autowired
    private CategoryService categoryService;

    @Autowired
    private FileStorageService fileStorageService;

    @Autowired
    private VideoResourceRepository videoResourceRepository;

    @Autowired
    private NotificationService notificationService;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private EnrollmentRepository enrollmentRepository;

    @GetMapping({"", "/"})
    public String adminRoot() {
        return "redirect:/admin/dashboard";
    }

    @GetMapping("/dashboard")
    public String dashboard(Model model) {
        // Analytics
        long totalUsers = userService.countUsers();
        long totalCourses = courseService.countCourses();
        long totalVideos = lectureRepository.count();
        long activeUsersToday = userService.countActiveUsers24h();

        // Most watched course & Total Views
        List<Course> allCourses = courseService.getAllCourses();
        long totalViews = 0;
        for (Course c : allCourses) {
            if (c.getViewCount() != null) {
                totalViews += c.getViewCount();
            }
        }
        Course mostWatched = courseRepository.findTopByOrderByViewCountDesc();

        // Pending user reports
        long pendingReports = reportRepository.findByStatus(Report.ReportStatus.PENDING).size();

        model.addAttribute("totalUsers", totalUsers);
        model.addAttribute("totalCourses", totalCourses);
        model.addAttribute("totalVideos", totalVideos);
        model.addAttribute("activeUsersToday", activeUsersToday);
        model.addAttribute("totalViews", totalViews);
        model.addAttribute("mostWatchedCourse", mostWatched);
        model.addAttribute("pendingReports", pendingReports);

        return "admin/dashboard";
    }

    @GetMapping("/analytics")
    public String showAnalytics(Model model) {
        long totalUsers = userService.countUsers();
        long totalCourses = courseService.countCourses();
        long totalEnrollments = enrollmentRepository.count();
        long totalInstructors = userService.getAllUsers().stream()
                .filter(u -> "INSTRUCTOR".equals(u.getRole()))
                .count();

        long activeUsers = userService.countActiveUsers24h();
        if (activeUsers == 0 && totalUsers > 0) {
            activeUsers = userService.getAllUsers().stream().filter(u -> !u.isBlocked()).count();
        }
        long inactiveUsers = Math.max(0, totalUsers - activeUsers);

        // Last 7 days enrollment trajectory
        List<String> enrollmentLabels = new ArrayList<>();
        List<Long> enrollmentValues = new ArrayList<>();
        java.time.LocalDate today = java.time.LocalDate.now();
        java.time.format.DateTimeFormatter formatter = java.time.format.DateTimeFormatter.ofPattern("MMM dd");

        List<Enrollment> allEnrollments = enrollmentRepository.findAll();
        for (int i = 6; i >= 0; i--) {
            java.time.LocalDate date = today.minusDays(i);
            enrollmentLabels.add(date.format(formatter));
            long count = allEnrollments.stream()
                    .filter(e -> e.getEnrolledAt() != null && e.getEnrolledAt().toLocalDate().isEqual(date))
                    .count();
            enrollmentValues.add(count);
        }

        // Popular Categories & their enrollment count
        List<Category> categories = categoryService.getAllCategories();
        List<String> categoryLabels = new ArrayList<>();
        List<Long> categoryValues = new ArrayList<>();
        if (categories != null) {
            for (Category cat : categories) {
                categoryLabels.add(cat.getName());
                long count = allEnrollments.stream()
                        .filter(e -> e.getCourse() != null && e.getCourse().getCategory() != null
                                && cat.getName() != null
                                && cat.getName().equalsIgnoreCase(e.getCourse().getCategory().getName()))
                        .count();
                categoryValues.add(count);
            }
        }

        model.addAttribute("totalUsers", totalUsers);
        model.addAttribute("totalCourses", totalCourses);
        model.addAttribute("totalEnrollments", totalEnrollments);
        model.addAttribute("totalInstructors", totalInstructors);
        model.addAttribute("activeUsers", activeUsers);
        model.addAttribute("inactiveUsers", inactiveUsers);
        model.addAttribute("enrollmentLabels", enrollmentLabels);
        model.addAttribute("enrollmentValues", enrollmentValues);
        model.addAttribute("categoryLabels", categoryLabels);
        model.addAttribute("categoryValues", categoryValues);

        return "admin/analytics";
    }

    @GetMapping("/users")
    public String listUsers(Model model) {
        // EXCLUDE INSTRUCTORS FROM THE STANDARD USERS TAB
        List<User> studentsAndAdmins = userService.getAllUsers().stream()
                .filter(u -> !"INSTRUCTOR".equals(u.getRole()))
                .collect(Collectors.toList());
        model.addAttribute("users", studentsAndAdmins);
        return "admin/users";
    }

    @GetMapping("/vendors")
    public String listVendors(Model model) {
        // ONLY INSTRUCTORS FOR THE VENDORS TAB
        List<User> vendors = userService.getAllUsers().stream()
                .filter(u -> "INSTRUCTOR".equals(u.getRole()))
                .collect(Collectors.toList());
        model.addAttribute("users", vendors); // Keeping 'users' attribute name for easy template reuse
        return "admin/vendors";
    }

    @PostMapping("/vendors/add")
    public String addVendor(@RequestParam("name") String name,
                            @RequestParam("email") String email,
                            @RequestParam("password") String password,
                            RedirectAttributes redirectAttributes) {
        
        if (userService.findByEmail(email) != null) {
            redirectAttributes.addFlashAttribute("error", "User with this email already exists.");
            return "redirect:/admin/vendors";
        }
        
        User vendor = new User();
        vendor.setName(name);
        vendor.setEmail(email);
        vendor.setPassword(passwordEncoder.encode(password));
        vendor.setRole("INSTRUCTOR");
        vendor.setApproved(true);
        userService.saveUser(vendor);
        
        redirectAttributes.addFlashAttribute("success", "Vendor added successfully.");
        return "redirect:/admin/vendors";
    }

    @PostMapping("/users/block")
    public String blockUser(@RequestParam("id") Long id, HttpServletRequest request) {
        userService.blockUser(id);
        String referer = request.getHeader("Referer");
        return referer != null ? "redirect:" + referer : "redirect:/admin/users";
    }

    @PostMapping("/users/unblock")
    public String unblockUser(@RequestParam("id") Long id, HttpServletRequest request) {
        userService.unblockUser(id);
        String referer = request.getHeader("Referer");
        return referer != null ? "redirect:" + referer : "redirect:/admin/users";
    }

    @PostMapping("/users/approve")
    public String approveUser(@RequestParam("id") Long id, HttpServletRequest request) {
        userService.approveUser(id);
        String referer = request.getHeader("Referer");
        return referer != null ? "redirect:" + referer : "redirect:/admin/users";
    }

    @PostMapping("/users/delete")
    public String deleteUser(@RequestParam("id") Long id, HttpServletRequest request) {
        userService.deleteUser(id);
        String referer = request.getHeader("Referer");
        return referer != null ? "redirect:" + referer : "redirect:/admin/users";
    }

    @GetMapping("/courses")
    public String listCourses(@RequestParam(value = "status", required = false) String statusFilter, Model model) {
        List<Course> allCourses = courseService.getAllCourses();
        List<Course> displayedCourses = allCourses;

        if (statusFilter != null && !statusFilter.trim().isEmpty() && !"ALL".equalsIgnoreCase(statusFilter)) {
            try {
                CourseStatus cs = CourseStatus.valueOf(statusFilter.toUpperCase());
                displayedCourses = allCourses.stream()
                        .filter(c -> c.getStatus() == cs)
                        .collect(Collectors.toList());
            } catch (Exception ignored) {
            }
        }

        long pendingCount = courseService.countPendingCourses();
        model.addAttribute("courses", displayedCourses);
        model.addAttribute("allCourses", allCourses);
        model.addAttribute("pendingCount", pendingCount);
        model.addAttribute("statusFilter", statusFilter != null ? statusFilter : "ALL");
        return "admin/courses";
    }

    @GetMapping("/courses/{id}/review")
    public String reviewCourse(@PathVariable("id") Long id, Model model) {
        Course course = courseService.getCourseById(id);
        if (course == null) {
            return "redirect:/admin/courses?error=CourseNotFound";
        }
        model.addAttribute("course", course);
        model.addAttribute("categoryDataMap", course.getCategoryDataMap());
        return "admin/course_review";
    }

    @PostMapping("/courses/{id}/approve")
    public String approveCourse(@PathVariable("id") Long id, RedirectAttributes redirectAttributes) {
        Course course = courseService.getCourseById(id);
        if (course != null) {
            courseService.approveCourse(id);
            if (course.getInstructor() != null) {
                notificationService.createAndSend(course.getInstructor(),
                        "Course Approved & Published!",
                        "Congratulations! Your course '" + course.getTitle() + "' has been approved by admin and is now live on SkillHub.",
                        "SUCCESS");
            }
            redirectAttributes.addFlashAttribute("success", "Course '" + course.getTitle() + "' approved and published successfully!");
        }
        return "redirect:/admin/courses";
    }

    @PostMapping("/courses/{id}/reject")
    public String rejectCourse(@PathVariable("id") Long id, @RequestParam("reason") String reason, RedirectAttributes redirectAttributes) {
        Course course = courseService.getCourseById(id);
        if (course != null) {
            courseService.rejectCourse(id, reason);
            if (course.getInstructor() != null) {
                notificationService.createAndSend(course.getInstructor(),
                        "Changes Requested: " + course.getTitle(),
                        "Admin requested changes on your course: " + reason,
                        "WARNING");
            }
            redirectAttributes.addFlashAttribute("warning", "Course rejected. Changes have been requested from the instructor.");
        }
        return "redirect:/admin/courses";
    }

    @PostMapping("/courses/delete")
    public String deleteCourse(@RequestParam("id") Long id) {
        courseService.deleteCourse(id);
        return "redirect:/admin/courses";
    }

    @PostMapping("/courses/publish")
    public String publishCourse(@RequestParam("id") Long id) {
        courseService.publishCourse(id);
        return "redirect:/admin/courses";
    }


    @GetMapping("/courses/create")
    public String createDraftAndRedirect(Principal principal) {
        User instructor = userService.findByEmail(principal.getName());

        Course course = new Course();
        course.setTitle("Untitled Course");
        course.setInstructor(instructor);
        course.setPublished(true);
        course.setViewCount(0L);
        course.setPrice(0.0); // Default to avoid null errors

        courseService.saveCourse(course);

        return "redirect:/admin/courses/edit?id=" + course.getId();
    }

    @GetMapping("/courses/edit")
    public String editCourse(@RequestParam("id") Long id, Model model) {
        Course course = courseService.getCourseById(id);
        model.addAttribute("course", course);
        model.addAttribute("categories", categoryService.getAllCategories());
        return "admin/course_form";
    }

    @PostMapping("/courses/save")
    public String saveCourse(
            @ModelAttribute("course") Course course,
            @RequestParam(name = "videoFile", required = false) MultipartFile videoFile,
            @RequestParam(name = "youtubeUrl", required = false) String youtubeUrl,
            Principal principal) {

        if (course.getId() != null) {
            // Update existing course
            Course existing = courseService.getCourseById(course.getId());
            if (existing != null) {
                existing.setTitle(course.getTitle());
                existing.setSubtitle(course.getSubtitle());
                existing.setCategory(course.getCategory());
                existing.setSubCategory(course.getSubCategory());
                existing.setPrice(course.getPrice());
                existing.setLevel(course.getLevel());
                existing.setLanguage(course.getLanguage());
                existing.setImageUrl(course.getImageUrl());
                existing.setLongDescription(course.getLongDescription());
                existing.setLearningObjectives(course.getLearningObjectives());
                existing.setRequirements(course.getRequirements());

                // Handle Video Update
                if (youtubeUrl != null && !youtubeUrl.trim().isEmpty()) {
                    existing.setVideoUrl(youtubeUrl);
                } else if (videoFile != null && !videoFile.isEmpty()) {
                    String path = fileStorageService.store(videoFile);
                    existing.setVideoUrl(path);
                }

                // Preserve instructor, published status, view count, etc.
                courseService.saveCourse(existing);
            }
        } else {
            // Create new course
            User instructor = userService.findByEmail(principal.getName());
            course.setInstructor(instructor);
            course.setPublished(true); // Always published

            // Handle Video Upload
            if (youtubeUrl != null && !youtubeUrl.trim().isEmpty()) {
                course.setVideoUrl(youtubeUrl);
            } else if (videoFile != null && !videoFile.isEmpty()) {
                String path = fileStorageService.store(videoFile);
                course.setVideoUrl(path);
            }

            courseService.saveCourse(course);
        }
        return "redirect:/admin/courses/edit?id=" + course.getId();
    }

    @GetMapping("/categories")
    public String listCategories(Model model) {
        model.addAttribute("categories", categoryService.getRootCategories());
        model.addAttribute("allCategories", categoryService.getAllCategories());
        model.addAttribute("newCategory", new Category());
        return "admin/categories";
    }

    @PostMapping("/categories/add")
    public String addCategory(
            @ModelAttribute Category category,
            RedirectAttributes redirectAttributes) {
        if (category.getIcon() == null || category.getIcon().isEmpty()) {
            category.setIcon("fas fa-folder");
        }
        categoryService.saveCategory(category);
        redirectAttributes.addFlashAttribute("success", "Category created successfully!");
        return "redirect:/admin/categories";
    }

    @PostMapping("/categories/edit")
    public String editCategory(
            @RequestParam("id") Long id,
            @RequestParam("name") String name,
            @RequestParam("icon") String icon,
            RedirectAttributes redirectAttributes) {
        Category category = categoryService.getCategoryById(id);
        if (category != null) {
            category.setName(name);
            category.setIcon(icon);
            categoryService.saveCategory(category);
            redirectAttributes.addFlashAttribute("success", "Category updated successfully!");
        }
        return "redirect:/admin/categories";
    }

    @PostMapping("/categories/delete")
    public String deleteCategory(@RequestParam("id") Long id,
                               RedirectAttributes redirectAttributes) {
        try {
            categoryService.deleteCategory(id);
            redirectAttributes.addFlashAttribute("success", "Category deleted successfully!");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", "Cannot delete category. It might be linked to courses.");
        }
        return "redirect:/admin/categories";
    }

    @GetMapping("/upload")
    public String showUploadForm(Model model) {
        model.addAttribute("categories", categoryService.getAllCategories());
        return "admin/upload";
    }

    @PostMapping("/upload")
    public String uploadVideo(
            @RequestParam(name = "file", required = false) MultipartFile file,
            @RequestParam(name = "title") String title,
            @RequestParam(name = "categoryId") Long categoryId,
            @RequestParam(name = "youtubeUrl", required = false) String youtubeUrl,
            RedirectAttributes redirectAttributes) {

        if ((file == null || file.isEmpty()) && (youtubeUrl == null || youtubeUrl.trim().isEmpty())) {
            redirectAttributes.addFlashAttribute("error", "Please provide a video file OR a YouTube URL.");
            return "redirect:/admin/upload";
        }

        VideoResource video = new VideoResource();
        video.setTitle(title);
        video.setCategory(categoryService.getCategoryById(categoryId));
        video.setUploadDate(LocalDateTime.now());

        if (youtubeUrl != null && !youtubeUrl.trim().isEmpty()) {
            video.setFilePath(youtubeUrl);
            video.setDescription("YouTube Video");
        } else {
            String path = fileStorageService.store(file);
            video.setFilePath(path);
            video.setDescription("Uploaded Video");
        }

        videoResourceRepository.save(video);

        redirectAttributes.addFlashAttribute("success",
                "Video resource added successfully! You can find it in the Video Library.");
        return "redirect:/library";
    }

    @GetMapping("/broadcast")
    public String showBroadcastForm() {
        return "admin/broadcast";
    }

    @PostMapping("/broadcast")
    public String sendBroadcast(@RequestParam("subject") String subject,
            @RequestParam("message") String message,
            RedirectAttributes redirectAttributes) {

        // Mock email sending
        System.out.println("BROADCAST EMAIL: Subject=" + subject + ", Message=" + message);

        // Send System Notification to ALL users (Effective Broadcast)
        List<User> allUsers = userService.getAllUsers();
        for (User user : allUsers) {
            notificationService.createAndSend(user, subject, message, "INFO");
        }

        redirectAttributes.addFlashAttribute("success", "Broadcast sent to " + allUsers.size() + " users!");
        return "redirect:/admin/broadcast";
    }

    @GetMapping("/profile")
    public String adminProfile(Model model, Principal principal) {
        if (principal != null) {
            model.addAttribute("admin", userService.findByEmail(principal.getName()));
        }
        return "admin/profile";
    }

    @PostMapping("/change-password")
    public String changePassword(@RequestParam("oldPassword") String oldPassword,
            @RequestParam("newPassword") String newPassword,
            @RequestParam("confirmPassword") String confirmPassword,
            Principal principal,
            RedirectAttributes redirectAttributes) {

        if (principal == null) {
            return "redirect:/login";
        }

        User admin = userService.findByEmail(principal.getName());

        if (!passwordEncoder.matches(oldPassword, admin.getPassword())) {
            redirectAttributes.addFlashAttribute("error", "Incorrect current password.");
            return "redirect:/admin/profile";
        }

        if (!newPassword.equals(confirmPassword)) {
            redirectAttributes.addFlashAttribute("error", "New passwords do not match.");
            return "redirect:/admin/profile";
        }

        admin.setPassword(passwordEncoder.encode(newPassword));
        userService.updateUser(admin);

        redirectAttributes.addFlashAttribute("success", "Password changed successfully.");
        return "redirect:/admin/profile";
    }

    @GetMapping("/reports")
    public String listReports(Model model) {
        model.addAttribute("reports", reportRepository.findAll());
        return "admin/reports";
    }

    @PostMapping("/reports/status")
    public String updateReportStatus(@RequestParam("id") Long id, 
                                   @RequestParam("status") Report.ReportStatus status) {
        reportRepository.findById(id).ifPresent(report -> {
            report.setStatus(status);
            reportRepository.save(report);
        });
        return "redirect:/admin/reports";
    }

    @GetMapping("/exams")
    public String listExams(Model model, Principal principal) {
        if (principal != null) {
            model.addAttribute("user", userService.findByEmail(principal.getName()));
        }
        model.addAttribute("courses", courseService.getAllCourses());
        return "admin/exams";
    }

    @GetMapping("/courses/{id}/questions")
    public String manageExamQuestions(
            @PathVariable("id") Long id,
            @RequestParam(value = "typeFilter", required = false) String typeFilter,
            @RequestParam(value = "search", required = false) String search,
            Model model, Principal principal) {
        Course course = courseService.getCourseById(id);
        if (principal != null) {
            model.addAttribute("user", userService.findByEmail(principal.getName()));
        }
        model.addAttribute("course", course);

        List<ExamQuestion> questions = course != null && course.getExamQuestions() != null
                ? new ArrayList<>(course.getExamQuestions())
                : new ArrayList<>();

        if (typeFilter != null && !typeFilter.trim().isEmpty() && !"ALL".equalsIgnoreCase(typeFilter)) {
            questions = questions.stream()
                    .filter(q -> q.getQuestionType() != null && q.getQuestionType().name().equalsIgnoreCase(typeFilter.trim()))
                    .collect(Collectors.toList());
        }

        if (search != null && !search.trim().isEmpty()) {
            String lower = search.trim().toLowerCase();
            questions = questions.stream()
                    .filter(q -> q.getQuestionText() != null && q.getQuestionText().toLowerCase().contains(lower))
                    .collect(Collectors.toList());
        }

        model.addAttribute("questions", questions);
        model.addAttribute("totalQuestionsCount", course != null && course.getExamQuestions() != null ? course.getExamQuestions().size() : 0);
        model.addAttribute("questionTypes", CourseQuestionType.values());
        model.addAttribute("selectedTypeFilter", typeFilter != null ? typeFilter : "ALL");
        model.addAttribute("searchQuery", search != null ? search : "");
        return "admin/manage_questions";
    }

    @PostMapping("/courses/{id}/questions/add")
    public String addExamQuestion(@PathVariable("id") Long id,
            @RequestParam(value = "questionType", defaultValue = "MULTIPLE_CHOICE") String questionType,
            @RequestParam("questionText") String questionText,
            @RequestParam(value = "marks", defaultValue = "1") Integer marks,
            @RequestParam(value = "explanation", required = false) String explanation,
            @RequestParam(value = "typeData", required = false) String typeData,
            @RequestParam(value = "correctAnswer", required = false) String correctAnswer,
            @RequestParam(value = "manualGrading", defaultValue = "false") Boolean manualGrading,
            @RequestParam(value = "caseSensitive", defaultValue = "false") Boolean caseSensitive,
            @RequestParam(value = "tolerance", required = false) Double tolerance,
            @RequestParam(value = "codeLanguage", required = false) String codeLanguage,
            // Legacy params for backward compatibility
            @RequestParam(value = "optionA", required = false) String optionA,
            @RequestParam(value = "optionB", required = false) String optionB,
            @RequestParam(value = "optionC", required = false) String optionC,
            @RequestParam(value = "optionD", required = false) String optionD,
            @RequestParam(value = "correctOption", required = false) String correctOption,
            RedirectAttributes redirectAttributes) {

        Course course = courseService.getCourseById(id);
        if (course != null) {
            CourseQuestionType type = CourseQuestionType.fromString(questionType);
            ExamQuestion q = new ExamQuestion();
            q.setCourse(course);
            q.setQuestionType(type);
            q.setQuestionText(questionText != null ? questionText.trim() : "");
            q.setMarks(marks != null && marks > 0 ? marks : 1);
            q.setExplanation(explanation);
            q.setTypeData(typeData != null ? typeData.trim() : "{}");
            q.setManualGrading(manualGrading);
            q.setCaseSensitive(caseSensitive);
            q.setTolerance(tolerance);
            q.setCodeLanguage(codeLanguage);

            // Backward compatibility for MULTIPLE_CHOICE
            q.setOptionA(optionA != null ? optionA : "");
            q.setOptionB(optionB != null ? optionB : "");
            q.setOptionC(optionC != null ? optionC : "");
            q.setOptionD(optionD != null ? optionD : "");
            q.setCorrectOption(correctOption != null ? correctOption : "");

            // Canonical correct answer
            if (correctAnswer != null && !correctAnswer.trim().isEmpty()) {
                q.setCorrectAnswer(correctAnswer.trim());
            } else if (correctOption != null && !correctOption.trim().isEmpty()) {
                q.setCorrectAnswer(correctOption.trim());
            }

            course.getExamQuestions().add(q);
            courseService.saveCourse(course);
            redirectAttributes.addFlashAttribute("successMessage", "Question added successfully!");
        }
        return "redirect:/admin/courses/" + id + "/questions";
    }

    @PostMapping("/courses/{id}/questions/update/{qid}")
    public String updateExamQuestion(@PathVariable("id") Long id,
            @PathVariable("qid") Long qid,
            @RequestParam(value = "questionType", defaultValue = "MULTIPLE_CHOICE") String questionType,
            @RequestParam("questionText") String questionText,
            @RequestParam(value = "marks", defaultValue = "1") Integer marks,
            @RequestParam(value = "explanation", required = false) String explanation,
            @RequestParam(value = "typeData", required = false) String typeData,
            @RequestParam(value = "correctAnswer", required = false) String correctAnswer,
            @RequestParam(value = "manualGrading", defaultValue = "false") Boolean manualGrading,
            @RequestParam(value = "caseSensitive", defaultValue = "false") Boolean caseSensitive,
            @RequestParam(value = "tolerance", required = false) Double tolerance,
            @RequestParam(value = "codeLanguage", required = false) String codeLanguage,
            @RequestParam(value = "optionA", required = false) String optionA,
            @RequestParam(value = "optionB", required = false) String optionB,
            @RequestParam(value = "optionC", required = false) String optionC,
            @RequestParam(value = "optionD", required = false) String optionD,
            @RequestParam(value = "correctOption", required = false) String correctOption,
            RedirectAttributes redirectAttributes) {

        Course course = courseService.getCourseById(id);
        if (course != null && course.getExamQuestions() != null) {
            for (ExamQuestion q : course.getExamQuestions()) {
                if (q.getId() != null && q.getId().equals(qid)) {
                    CourseQuestionType type = CourseQuestionType.fromString(questionType);
                    q.setQuestionType(type);
                    q.setQuestionText(questionText != null ? questionText.trim() : "");
                    q.setMarks(marks != null && marks > 0 ? marks : 1);
                    q.setExplanation(explanation);
                    q.setTypeData(typeData != null ? typeData.trim() : "{}");
                    q.setManualGrading(manualGrading);
                    q.setCaseSensitive(caseSensitive);
                    q.setTolerance(tolerance);
                    q.setCodeLanguage(codeLanguage);

                    q.setOptionA(optionA != null ? optionA : "");
                    q.setOptionB(optionB != null ? optionB : "");
                    q.setOptionC(optionC != null ? optionC : "");
                    q.setOptionD(optionD != null ? optionD : "");
                    q.setCorrectOption(correctOption != null ? correctOption : "");

                    if (correctAnswer != null && !correctAnswer.trim().isEmpty()) {
                        q.setCorrectAnswer(correctAnswer.trim());
                    } else if (correctOption != null && !correctOption.trim().isEmpty()) {
                        q.setCorrectAnswer(correctOption.trim());
                    }
                    break;
                }
            }
            courseService.saveCourse(course);
            redirectAttributes.addFlashAttribute("successMessage", "Question updated successfully!");
        }
        return "redirect:/admin/courses/" + id + "/questions";
    }

    @GetMapping("/courses/{id}/questions/{qid}/json")
    @ResponseBody
    public ResponseEntity<?> getExamQuestionJson(@PathVariable("id") Long id, @PathVariable("qid") Long qid) {
        Course course = courseService.getCourseById(id);
        if (course != null && course.getExamQuestions() != null) {
            for (ExamQuestion q : course.getExamQuestions()) {
                if (q.getId() != null && q.getId().equals(qid)) {
                    Map<String, Object> data = new HashMap<>();
                    data.put("id", q.getId());
                    data.put("questionType", q.getQuestionType().name());
                    data.put("questionText", q.getQuestionText());
                    data.put("marks", q.getMarks());
                    data.put("explanation", q.getExplanation());
                    data.put("typeData", q.getTypeData());
                    data.put("correctAnswer", q.getCorrectAnswer());
                    data.put("manualGrading", q.getManualGrading());
                    data.put("caseSensitive", q.getCaseSensitive());
                    data.put("tolerance", q.getTolerance());
                    data.put("codeLanguage", q.getCodeLanguage());
                    data.put("optionA", q.getOptionA());
                    data.put("optionB", q.getOptionB());
                    data.put("optionC", q.getOptionC());
                    data.put("optionD", q.getOptionD());
                    data.put("correctOption", q.getCorrectOption());
                    return ResponseEntity.ok(data);
                }
            }
        }
        return ResponseEntity.notFound().build();
    }

    @PostMapping("/courses/{id}/questions/delete/{qid}")
    public String deleteExamQuestion(@PathVariable("id") Long id,
            @PathVariable("qid") Long qid,
            RedirectAttributes redirectAttributes) {
        Course course = courseService.getCourseById(id);
        if (course != null) {
            course.getExamQuestions().removeIf(q -> q.getId().equals(qid));
            courseService.saveCourse(course);
            redirectAttributes.addFlashAttribute("successMessage", "Question deleted successfully!");
        }
        return "redirect:/admin/courses/" + id + "/questions";
    }
}
