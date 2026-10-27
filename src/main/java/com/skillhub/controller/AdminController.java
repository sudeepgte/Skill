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
import java.util.Objects;
import java.util.Set;
import java.util.function.Predicate;
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

    @Autowired
    private ReviewRepository reviewRepository;

    @Autowired
    private ExamAttemptRepository examAttemptRepository;

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
        List<User> vendors = userService.getAllUsers().stream()
                .filter(u -> "INSTRUCTOR".equals(u.getRole()))
                .collect(Collectors.toList());
        model.addAttribute("users", vendors);

        Map<Long, Map<String, Object>> vendorDetails = new HashMap<>();
        for (User v : vendors) {
            String email = v.getEmail();
            Map<String, Object> data = new HashMap<>();
            List<Course> courses = courseService.getCoursesByInstructor(email);
            Long viewsObj = courseService.sumViewsByInstructorEmail(email);
            long totalViews = viewsObj != null ? viewsObj : 0L;
            for (Course c : courses) {
                if (c.getViewCount() != null) totalViews += c.getViewCount();
            }
            long totalStudents = enrollmentRepository.countByInstructorEmail(email);
            List<Review> reviews = reviewRepository != null ? reviewRepository.findByInstructorEmail(email) : new ArrayList<>();
            
            int totalExams = 0;
            List<Map<String, Object>> courseList = new ArrayList<>();
            for (Course c : courses) {
                Map<String, Object> cMap = new HashMap<>();
                cMap.put("id", c.getId());
                cMap.put("title", c.getTitle());
                cMap.put("category", c.getCategory() != null ? c.getCategory().getName() : "General");
                cMap.put("status", c.getStatus() != null ? c.getStatus().name() : (c.isPublished() ? "PUBLISHED" : "DRAFT"));
                cMap.put("price", c.getPrice() != null ? c.getPrice() : 0.0);
                cMap.put("views", c.getViewCount() != null ? c.getViewCount() : 0);
                cMap.put("students", c.getEnrollments() != null ? c.getEnrollments().size() : 0);
                int qCount = c.getExamQuestions() != null ? c.getExamQuestions().size() : 0;
                cMap.put("examQuestions", qCount);
                if (qCount > 0) totalExams++;
                courseList.add(cMap);
            }

            List<Map<String, Object>> reviewList = new ArrayList<>();
            double totalStars = 0;
            for (Review r : reviews) {
                Map<String, Object> rMap = new HashMap<>();
                rMap.put("id", r.getId());
                rMap.put("studentName", r.getUser() != null ? r.getUser().getName() : "Student");
                rMap.put("rating", r.getRating());
                rMap.put("comment", r.getComment());
                rMap.put("courseTitle", r.getCourse() != null ? r.getCourse().getTitle() : "Course");
                rMap.put("date", r.getCreatedAt() != null ? r.getCreatedAt().toString() : "");
                rMap.put("reply", r.getReply());
                reviewList.add(rMap);
                totalStars += r.getRating();
            }

            data.put("courseCount", courses.size());
            data.put("totalViews", totalViews);
            data.put("totalStudents", totalStudents);
            data.put("totalExams", totalExams);
            data.put("reviewCount", reviews.size());
            data.put("avgRating", !reviews.isEmpty() ? Math.round((totalStars / reviews.size()) * 10.0) / 10.0 : 0.0);
            data.put("courses", courseList);
            data.put("reviews", reviewList);

            vendorDetails.put(v.getId(), data);
        }
        model.addAttribute("vendorDetails", vendorDetails);
        return "admin/vendors";
    }

    @GetMapping("/vendors/{id}/data")
    @ResponseBody
    public Map<String, Object> getVendorDataJson(@PathVariable("id") Long id) {
        User vendor = userService.getUserById(id);
        if (vendor == null) return new HashMap<>();
        String email = vendor.getEmail();
        Map<String, Object> data = new HashMap<>();
        List<Course> courses = courseService.getCoursesByInstructor(email);
        Long viewsObj = courseService.sumViewsByInstructorEmail(email);
        long totalViews = viewsObj != null ? viewsObj : 0L;
        for (Course c : courses) {
            if (c.getViewCount() != null) totalViews += c.getViewCount();
        }
        long totalStudents = enrollmentRepository.countByInstructorEmail(email);
        List<Review> reviews = reviewRepository != null ? reviewRepository.findByInstructorEmail(email) : new ArrayList<>();

        int totalExams = 0;
        List<Map<String, Object>> courseList = new ArrayList<>();
        for (Course c : courses) {
            Map<String, Object> cMap = new HashMap<>();
            cMap.put("id", c.getId());
            cMap.put("title", c.getTitle());
            cMap.put("category", c.getCategory() != null ? c.getCategory().getName() : "General");
            cMap.put("status", c.getStatus() != null ? c.getStatus().name() : (c.isPublished() ? "PUBLISHED" : "DRAFT"));
            cMap.put("price", c.getPrice() != null ? c.getPrice() : 0.0);
            cMap.put("views", c.getViewCount() != null ? c.getViewCount() : 0);
            cMap.put("students", c.getEnrollments() != null ? c.getEnrollments().size() : 0);
            int qCount = c.getExamQuestions() != null ? c.getExamQuestions().size() : 0;
            cMap.put("examQuestions", qCount);
            if (qCount > 0) totalExams++;
            courseList.add(cMap);
        }

        List<Map<String, Object>> reviewList = new ArrayList<>();
        double totalStars = 0;
        for (Review r : reviews) {
            Map<String, Object> rMap = new HashMap<>();
            rMap.put("id", r.getId());
            rMap.put("studentName", r.getUser() != null ? r.getUser().getName() : "Student");
            rMap.put("rating", r.getRating());
            rMap.put("comment", r.getComment());
            rMap.put("courseTitle", r.getCourse() != null ? r.getCourse().getTitle() : "Course");
            rMap.put("date", r.getCreatedAt() != null ? r.getCreatedAt().toString() : "");
            rMap.put("reply", r.getReply());
            reviewList.add(rMap);
            totalStars += r.getRating();
        }

        data.put("courseCount", courses.size());
        data.put("totalViews", totalViews);
        data.put("totalStudents", totalStudents);
        data.put("totalExams", totalExams);
        data.put("reviewCount", reviews.size());
        data.put("avgRating", !reviews.isEmpty() ? Math.round((totalStars / reviews.size()) * 10.0) / 10.0 : 0.0);
        data.put("courses", courseList);
        data.put("reviews", reviewList);
        return data;
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
    public String deleteCourse(@RequestParam("id") Long id, RedirectAttributes redirectAttributes) {
        try {
            courseService.deleteCourse(id);
            redirectAttributes.addFlashAttribute("success", "Course deleted successfully!");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("warning", "Error deleting course: " + e.getMessage());
        }
        return "redirect:/admin/courses";
    }

    @GetMapping("/courses/{id}/delete")
    public String deleteCourseGet(@PathVariable("id") Long id, RedirectAttributes redirectAttributes) {
        try {
            courseService.deleteCourse(id);
            redirectAttributes.addFlashAttribute("success", "Course deleted successfully!");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("warning", "Error deleting course: " + e.getMessage());
        }
        return "redirect:/admin/courses";
    }

    @PostMapping("/courses/publish")
    public String publishCourse(@RequestParam("id") Long id) {
        courseService.publishCourse(id);
        return "redirect:/admin/courses";
    }

    @GetMapping("/courses/create")
    public String showCreateCourseForm(Model model, Principal principal) {
        Course course = new Course();
        course.setLevel("All Levels");
        course.setLanguage("English");
        course.setFormat("Video Course");
        course.setTeachingMethod("Self-paced");
        course.setAccessDuration("LIFETIME");
        course.setPrice(0.0);
        course.setSections(new ArrayList<>());
        model.addAttribute("course", course);
        model.addAttribute("categories", categoryService.getAllCategories());
        model.addAttribute("subcategoriesMap", categoryService.getCategorySubcategoriesMap());
        model.addAttribute("categoryFieldsMap", categoryService.getAllCategoryFieldDefinitions());
        model.addAttribute("categoryDataMap", new HashMap<String, String>());
        model.addAttribute("isEdit", false);
        model.addAttribute("isAdmin", true);
        return "admin/course_form";
    }

    @GetMapping({"/courses/edit", "/courses/{id}/edit"})
    public String editCourse(
            @RequestParam(value = "id", required = false) Long paramId,
            @PathVariable(value = "id", required = false) Long pathId,
            Model model,
            RedirectAttributes redirectAttributes) {
        Long id = paramId != null ? paramId : pathId;
        if (id == null) {
            return "redirect:/admin/courses/create";
        }
        Course course = courseService.getCourseById(id);
        if (course == null) {
            redirectAttributes.addFlashAttribute("warning", "Course not found.");
            return "redirect:/admin/courses";
        }
        model.addAttribute("course", course);
        model.addAttribute("categories", categoryService.getAllCategories());
        model.addAttribute("subcategoriesMap", categoryService.getCategorySubcategoriesMap());
        model.addAttribute("categoryFieldsMap", categoryService.getAllCategoryFieldDefinitions());
        model.addAttribute("categoryDataMap", course.getCategoryDataMap() != null ? course.getCategoryDataMap() : new HashMap<String, String>());
        model.addAttribute("isEdit", true);
        model.addAttribute("isAdmin", true);
        return "admin/course_form";
    }

    @PostMapping("/courses/save")
    public String saveCourse(
            @ModelAttribute Course course,
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

        String email = principal != null ? principal.getName() : null;
        User adminUser = email != null ? userService.findByEmail(email) : null;

        Course targetCourse;
        boolean isNew = (course.getId() == null);

        if (course.getPrice() != null && course.getPrice() < 0) {
            redirectAttributes.addFlashAttribute("error", "Course price cannot be negative.");
            return isNew ? "redirect:/admin/courses/create" : "redirect:/admin/courses/edit?id=" + course.getId();
        }

        // Validate file types before processing
        if (thumbnail != null && !thumbnail.isEmpty()) {
            String mimeType = thumbnail.getContentType();
            if (mimeType == null || !mimeType.startsWith("image/")) {
                redirectAttributes.addFlashAttribute("error", "Thumbnail must be an image file.");
                return isNew ? "redirect:/admin/courses/create" : "redirect:/admin/courses/edit?id=" + course.getId();
            }
        }
        if (promoVideo != null && !promoVideo.isEmpty()) {
            String mimeType = promoVideo.getContentType();
            if (mimeType == null || !mimeType.startsWith("video/")) {
                redirectAttributes.addFlashAttribute("error", "Promotional video must be a valid video file.");
                return isNew ? "redirect:/admin/courses/create" : "redirect:/admin/courses/edit?id=" + course.getId();
            }
        }

        if (isNew) {
            targetCourse = course;
            targetCourse.setInstructor(adminUser);
            targetCourse.setViewCount(0L);
        } else {
            targetCourse = courseService.getCourseById(course.getId());
            if (targetCourse == null) {
                redirectAttributes.addFlashAttribute("warning", "Course not found.");
                return "redirect:/admin/courses";
            }
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
        } else if (isNew || targetCourse.getImageUrl() == null || targetCourse.getImageUrl().trim().isEmpty() || targetCourse.getImageUrl().contains("udemycdn.com")) {
            targetCourse.setImageUrl("/images/course-placeholder.jpg");
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
            return isNew ? "redirect:/admin/courses/create" : "redirect:/admin/courses/edit?id=" + targetCourse.getId();
        }

        if (targetCourse.getDescription() != null && !targetCourse.getDescription().trim().isEmpty() && !startsWithLetter.test(targetCourse.getDescription())) {
            redirectAttributes.addFlashAttribute("error", "Course Description must start with a letter.");
            return isNew ? "redirect:/admin/courses/create" : "redirect:/admin/courses/edit?id=" + targetCourse.getId();
        }

        // Process Curriculum Videos and Validate Titles
        if (course.getSections() != null && !course.getSections().isEmpty()) {
            List<CourseSection> validSections = new ArrayList<>();
            for (CourseSection section : course.getSections()) {
                if (section.getTitle() != null && !section.getTitle().trim().isEmpty()) {
                    if (!startsWithLetter.test(section.getTitle())) {
                        redirectAttributes.addFlashAttribute("error", "Section titles must start with a letter.");
                        return isNew ? "redirect:/admin/courses/create" : "redirect:/admin/courses/edit?id=" + targetCourse.getId();
                    }
                    section.setCourse(targetCourse);
                    if (section.getLectures() != null) {
                        List<Lecture> validLectures = new ArrayList<>();
                        for (Lecture lecture : section.getLectures()) {
                            if (lecture.getTitle() != null && !lecture.getTitle().trim().isEmpty()) {
                                if (!startsWithLetter.test(lecture.getTitle())) {
                                    redirectAttributes.addFlashAttribute("error", "Lecture titles must start with a letter.");
                                    return isNew ? "redirect:/admin/courses/create" : "redirect:/admin/courses/edit?id=" + targetCourse.getId();
                                }
                                if (lecture.getVideoFile() != null && !lecture.getVideoFile().isEmpty()) {
                                    String lecMimeType = lecture.getVideoFile().getContentType();
                                    if (lecMimeType == null || !lecMimeType.startsWith("video/")) {
                                        redirectAttributes.addFlashAttribute("error", "Lecture video must be a valid video file.");
                                        return isNew ? "redirect:/admin/courses/create" : "redirect:/admin/courses/edit?id=" + targetCourse.getId();
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

        // Action Decision: "submit" / "publish" vs "draft"
        if ("submit".equalsIgnoreCase(action) || "publish".equalsIgnoreCase(action)) {
            // Validate required fields for publishing
            if (targetCourse.getDescription() == null || targetCourse.getDescription().trim().isEmpty()) {
                redirectAttributes.addFlashAttribute("error", "Course description is required to publish.");
                return isNew ? "redirect:/admin/courses/create" : "redirect:/admin/courses/edit?id=" + targetCourse.getId();
            }
            if (targetCourse.getCategory() == null || targetCourse.getCategory().getId() == null) {
                redirectAttributes.addFlashAttribute("error", "Please select a Category before publishing.");
                return isNew ? "redirect:/admin/courses/create" : "redirect:/admin/courses/edit?id=" + targetCourse.getId();
            }

            targetCourse.setStatus(CourseStatus.PUBLISHED);
            targetCourse.setPublished(true);
            targetCourse.setReviewedAt(LocalDateTime.now());
            targetCourse.setRejectionReason(null);
            courseService.saveCourse(targetCourse);
            redirectAttributes.addFlashAttribute("success", "Course '" + targetCourse.getTitle() + "' published successfully!");
        } else {
            // Save as Draft
            if (targetCourse.getStatus() != CourseStatus.PUBLISHED) {
                targetCourse.setStatus(CourseStatus.DRAFT);
                targetCourse.setPublished(false);
            }
            courseService.saveCourse(targetCourse);
            redirectAttributes.addFlashAttribute("success", "Course draft saved successfully!");
        }

        return "redirect:/admin/courses";
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
                .collect(Collectors.toSet());

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
                .collect(Collectors.toSet());

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
            notificationService.createAndSend(user, subject, message, "BROADCAST");
        }

        redirectAttributes.addFlashAttribute("success", "Broadcast notification sent successfully to all users and instructors.");
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
        // Only keep exams/courses added by instructors (vendors); exclude courses added by admin
        List<Course> instructorExams = courseService.getAllCourses().stream()
                .filter(c -> c.getInstructor() != null)
                .filter(c -> !"ADMIN".equalsIgnoreCase(c.getInstructor().getRole()))
                .filter(c -> c.getInstructor().getEmail() == null || !c.getInstructor().getEmail().toLowerCase().contains("admin"))
                .collect(Collectors.toList());
        model.addAttribute("courses", instructorExams);
        return "admin/exams";
    }

    @GetMapping("/exams/{courseId}/details")
    public String viewCourseExamDetails(@PathVariable("courseId") Long courseId, Model model, Principal principal) {
        if (principal != null) {
            model.addAttribute("user", userService.findByEmail(principal.getName()));
        }
        Course course = courseService.getCourseById(courseId);
        List<ExamQuestion> questions = (course != null && course.getExamQuestions() != null)
                ? course.getExamQuestions()
                : new ArrayList<>();
        int totalMarks = questions.stream().mapToInt(q -> q.getMarks() != null ? q.getMarks() : 1).sum();

        model.addAttribute("course", course);
        model.addAttribute("questions", questions);
        model.addAttribute("totalQuestions", questions.size());
        model.addAttribute("totalMarks", totalMarks);
        return "admin/exam_details";
    }

    @GetMapping("/exams/{courseId}/results")
    public String viewCourseExamResults(@PathVariable("courseId") Long courseId, Model model, Principal principal) {
        if (principal != null) {
            model.addAttribute("user", userService.findByEmail(principal.getName()));
        }
        Course course = courseService.getCourseById(courseId);
        List<ExamAttempt> attempts = course != null ? examAttemptRepository.findByCourse(course) : new ArrayList<>();
        // Sort descending: highest scores first, lowest last
        attempts.sort((a, b) -> Integer.compare(b.getScore(), a.getScore()));
        long passedCount = attempts.stream().filter(ExamAttempt::isPassed).count();
        long failedCount = attempts.size() - passedCount;
        double passRate = attempts.isEmpty() ? 0.0 : Math.round((passedCount * 100.0 / attempts.size()) * 10.0) / 10.0;
        double avgScore = attempts.isEmpty() ? 0.0 : Math.round((attempts.stream().mapToInt(ExamAttempt::getScore).average().orElse(0.0)) * 10.0) / 10.0;

        model.addAttribute("course", course);
        model.addAttribute("attempts", attempts);
        model.addAttribute("totalAttempts", attempts.size());
        model.addAttribute("passedCount", passedCount);
        model.addAttribute("failedCount", failedCount);
        model.addAttribute("passRate", passRate);
        model.addAttribute("avgScore", avgScore);
        return "admin/exam_results";
    }

    @GetMapping("/courses/{courseId}/users")
    public String viewCourseUsers(@PathVariable("courseId") Long courseId, Model model, Principal principal) {
        if (principal != null) {
            model.addAttribute("user", userService.findByEmail(principal.getName()));
        }
        Course course = courseService.getCourseById(courseId);
        List<Enrollment> enrollments = course != null ? enrollmentRepository.findByCourse(course) : new ArrayList<>();

        model.addAttribute("course", course);
        model.addAttribute("enrollments", enrollments);
        model.addAttribute("totalStudents", enrollments.size());
        return "admin/course_users";
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
